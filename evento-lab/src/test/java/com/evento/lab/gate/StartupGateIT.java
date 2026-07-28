package com.evento.lab.gate;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.gate.nowait.NoWaitProjector;
import com.evento.lab.gate.waiting.WaitingProjector;
import com.evento.lab.support.EmbeddedBroker;
import com.evento.lab.support.TestEventStoreBundleClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Bundle-enable timing against {@code @Projector(waitForHeadReached = ...)}.
 *
 * <p>Both scenarios pre-publish a backlog and block the projector's handler on a latch,
 * so the consumer verifiably cannot reach the event stream head until the test says so.
 * The default projector must not delay the bundle becoming available; the opted-in one
 * must hold the bundle disabled until it has aligned.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class StartupGateIT {

    private EmbeddedBroker broker;
    private TestEventStoreBundleClient eventStore;
    private EventoBundle bundle;

    @BeforeEach
    void setUp() throws Exception {
        GateStore.reset();
        broker = new EmbeddedBroker();
        eventStore = new TestEventStoreBundleClient(broker.port());
    }

    @AfterEach
    void tearDown() throws Exception {
        GateStore.gate.countDown(); // never leave a handler parked across tests
        if (bundle != null) {
            bundle.getEngineSupervisor().stop(Duration.ofSeconds(10));
            bundle = null;
        }
        if (eventStore != null) { eventStore.close(); eventStore = null; }
        if (broker != null) { broker.close(); broker = null; }
    }

    private void startBundle(String bundleId, Package basePackage) throws Exception {
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(basePackage)
                .setBundleId(bundleId)
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();
    }

    private void publishBacklog() {
        for (int i = 1; i <= 3; i++) {
            eventStore.publish(new OrderCreatedEvent("gate-" + i, "desc-" + i, i), "gate-" + i);
        }
    }

    @Test
    void defaultProjectorDoesNotGateBundleEnable() throws Exception {
        GateStore.gate = new CountDownLatch(1);
        publishBacklog();

        startBundle("lab-gate-nowait", NoWaitProjector.class.getPackage());

        // The handler is parked on the first event, so head is provably not reached —
        // yet the bundle must become available.
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> broker.lifecycle().isBundleAvailable("lab-gate-nowait"));
        assertThat(GateStore.applied)
                .as("bundle became available while the projector was still behind head")
                .isEmpty();

        // The projector keeps aligning in the background after the bundle is up.
        GateStore.gate.countDown();
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> GateStore.applied.size() == 3);
    }

    @Test
    void waitForHeadReachedHoldsBundleDisabledUntilAligned() throws Exception {
        GateStore.gate = new CountDownLatch(1);
        publishBacklog();

        startBundle("lab-gate-wait", WaitingProjector.class.getPackage());

        // Give startup ample time to (wrongly) enable: it must not while the handler
        // is parked short of head.
        Thread.sleep(2_000);
        assertThat(broker.lifecycle().isBundleAvailable("lab-gate-wait"))
                .as("a waitForHeadReached projector must hold the bundle disabled")
                .isFalse();

        GateStore.gate.countDown();
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> broker.lifecycle().isBundleAvailable("lab-gate-wait"));
        assertThat(GateStore.applied)
                .as("the whole backlog was consumed before the bundle became available")
                .hasSize(3);
    }
}
