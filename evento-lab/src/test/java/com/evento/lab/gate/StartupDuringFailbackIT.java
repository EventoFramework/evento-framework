package com.evento.lab.gate;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.client.BundleClient;
import com.evento.application.client.BundleClientState;
import com.evento.application.client.EventoServerAdapter;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.gate.failback.FailbackGatedProjector;
import com.evento.lab.support.EmbeddedBroker;
import com.evento.lab.support.TestEventStoreBundleClient;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The startup gate of an {@link EventoBundle} opening while the bundle is failing
 * back to its preferred broker (N16 review, HIGH-1).
 *
 * <p>When the last head-gating projector catches up, the startup thread enables
 * the bundle, starts the saga and observer engines, registers the consumers,
 * starts the stats publisher and runs the started hook. It used to do so through
 * sends that threw while the session was being drained for the switch: the
 * startup thread logged "Error during startup" and stopped there, leaving a
 * bundle that looked healthy (the enable was latched and re-sent on the next
 * broker) but whose sagas and observers never ran.
 *
 * <p>Broker A is the bundle's preferred broker and is down when the bundle starts,
 * so it attaches to B. A comes back; the bundle's own request to a slow handler
 * on B keeps the drain of the switch open while the test opens the gate.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class StartupDuringFailbackIT {

    private static final String SLOW = "com.evento.lab.test.Slow";

    private final List<AutoCloseable> closeables = new ArrayList<>();
    private EventoBundle bundle;

    @BeforeEach
    void setUp() {
        GateStore.reset();
    }

    @AfterEach
    void tearDown() {
        GateStore.gate.countDown();
        if (bundle != null) {
            bundle.getEngineSupervisor().stop(Duration.ofSeconds(10));
            ((EventoServerAdapter) bundle.getEventoServer()).client().stop(Duration.ofSeconds(1));
        }
        for (var c : closeables.reversed()) {
            try { c.close(); } catch (Exception ignored) {}
        }
    }

    @Test
    void theStartupThatCompletesDuringTheDrainStartsEverythingAndEnablesOnThePreferredBroker() throws Exception {
        var gone = new EmbeddedBroker("lab-broker-a", 0);
        int aPort = gone.port();
        gone.close();
        var b = new EmbeddedBroker("lab-broker-b", 0);
        closeables.add(b);
        var store = new TestEventStoreBundleClient(b.port());
        closeables.add(store);
        for (int i = 1; i <= 3; i++) {
            store.publish(new OrderCreatedEvent("fb-" + i, "desc-" + i, i), "fb-" + i);
        }

        var slowInvoked = new CountDownLatch(1);
        var slowRelease = new CountDownLatch(1);
        var slow = BundleClient.builder("lab-slow", "slow-1").bundleVersion("1")
                .endpoint("127.0.0.1", b.port()).handlerPayloadTypes(List.of(SLOW)).build();
        slow.registerRequestHandler(SLOW, (payload, ctx) -> {
            slowInvoked.countDown();
            slowRelease.await(60, TimeUnit.SECONDS);
            return "done".getBytes();
        });
        slow.start().get(10, TimeUnit.SECONDS);
        closeables.add(() -> slow.stop(Duration.ofSeconds(1)));
        closeables.add(slowRelease::countDown);

        GateStore.gate = new CountDownLatch(1);
        var startedOn = new AtomicReference<String>();
        var startedWhileDraining = new AtomicReference<Boolean>();
        var clientRef = new AtomicReference<BundleClient>();
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(FailbackGatedProjector.class.getPackage())
                .setBundleId("lab-gate-failback")
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", aPort),
                        new ClusterNodeAddress("127.0.0.1", b.port())))
                .setReconnectStrategy(new ExponentialBackoffWithJitter(Duration.ofMillis(50),
                        Duration.ofMillis(200), 0.0, ExponentialBackoffWithJitter.UNBOUNDED))
                .setFailbackProbeInterval(Duration.ofMillis(100))
                .setFailbackStabilityWindow(Duration.ofMillis(300))
                .setFailbackJitter(Duration.ZERO)
                .setFailbackDrainTimeout(Duration.ofSeconds(30))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .setOnEventoStartedHook(started -> {
                    var c = clientRef.get();
                    startedOn.set(c == null ? "?" : c.serverInstanceId());
                    startedWhileDraining.set(c != null && c.failbackDraining());
                })
                .start();
        var client = ((EventoServerAdapter) bundle.getEventoServer()).client();
        clientRef.set(client);
        String instanceId = bundle.getInstanceId();
        assertThat(client.serverInstanceId()).isEqualTo("lab-broker-b");
        // The projector is parked on the backlog: not enabled, sagas/observers not started.
        Thread.sleep(500);
        assertThat(b.lifecycle().isBundleAvailable("lab-gate-failback")).isFalse();
        assertThat(bundle.getEngineSupervisor().getObserverEngines()).isEmpty();

        // An outbound request of the bundle's keeps the coming drain open.
        var pending = client.request(SLOW, "x".getBytes(), Duration.ofSeconds(60));
        assertThat(slowInvoked.await(10, TimeUnit.SECONDS)).isTrue();

        var a = new EmbeddedBroker("lab-broker-a", aPort);
        closeables.add(a);
        await().atMost(Duration.ofSeconds(15)).until(client::failbackDraining);

        // The gate opens in the middle of the drain.
        GateStore.gate.countDown();
        await().atMost(Duration.ofSeconds(20)).until(() -> startedOn.get() != null);
        assertThat(startedOn.get()).as("started while still on the broker being left").isEqualTo("lab-broker-b");
        assertThat(startedWhileDraining.get()).isTrue();
        assertThat(bundle.getEngineSupervisor().getObserverEngines()).isNotEmpty();
        assertThat(GateStore.applied).hasSize(3);
        // The enable did not go to B, where the bundle is draining.
        assertThat(b.lifecycle().isBundleAvailable("lab-gate-failback")).isFalse();

        slowRelease.countDown();
        assertThat(pending.get(10, TimeUnit.SECONDS).isError()).isFalse();
        await().atMost(Duration.ofSeconds(15)).until(() -> client.state() == BundleClientState.READY
                && "lab-broker-a".equals(client.serverInstanceId()));
        assertThat(client.failbacksCompleted()).isEqualTo(1);
        // Enabled where it now is: the latched enable went out with the registration on A.
        await().atMost(Duration.ofSeconds(10)).until(() -> a.lifecycle().isBundleAvailable("lab-gate-failback"));
        assertThat(a.lifecycle().view()).anyMatch(n -> n.instanceId().equals(instanceId));
    }
}
