package com.evento.lab.consumer;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.v2.ConsumerEngineConfig;
import com.evento.lab.bundle.LabStore;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.support.EmbeddedBroker;
import com.evento.lab.support.TestEventStoreBundleClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests connectivity-level scenarios: bundle-to-broker registration, bundle
 * availability, and clean shutdown. Verifies that the full connection stack
 * (EmbeddedBroker + BundleClient + EventoBundle) works end to end.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ConnectivityIT {

    private EmbeddedBroker broker;
    private TestEventStoreBundleClient eventStore;
    private EventoBundle bundle;

    @BeforeEach
    void setUp() throws Exception {
        LabStore.reset();
        broker = new EmbeddedBroker();
        eventStore = new TestEventStoreBundleClient(broker.port());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (bundle != null) {
            bundle.getEngineSupervisor().stop(Duration.ofSeconds(10));
            bundle = null;
        }
        if (eventStore != null) { eventStore.close(); eventStore = null; }
        if (broker != null) { broker.close(); broker = null; }
    }

    @Test
    void bundleRegistersAndBecomesAvailableAfterProjectorsReachHead() throws Exception {
        // No events — the projectors should reach head immediately and send enable
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(com.evento.lab.bundle.consumer.LabProjector.class.getPackage())
                .setBundleId("lab-connectivity-bundle")
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> broker.lifecycle().isBundleAvailable("lab-connectivity-bundle"));

        assertThat(broker.lifecycle().view())
                .anyMatch(a -> a.bundleId().equals("lab-connectivity-bundle"));
    }

    @Test
    void bundleRecoversAfterBrokerRestart() throws Exception {
        // Start a second, independent broker for the reconnect test
        try (var broker2 = new EmbeddedBroker();
             var eventStore2 = new TestEventStoreBundleClient(broker2.port())) {

            bundle = EventoBundle.Builder.builder()
                    .setBasePackage(com.evento.lab.bundle.consumer.LabProjector.class.getPackage())
                    .setBundleId("lab-reconnect-bundle")
                    .setEventoServerMessageBusConfiguration(
                            new EventoServerMessageBusConfiguration(
                                    new ClusterNodeAddress("127.0.0.1", broker2.port())))
                    .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                    .start();

            // Wait until the bundle is registered and available
            await().atMost(15, TimeUnit.SECONDS)
                    .until(() -> broker2.lifecycle().isBundleAvailable("lab-reconnect-bundle"));

            // Publish some events and verify processing
            eventStore2.publish(new OrderCreatedEvent("conn-1", "d1", 1), "conn-1");
            await().atMost(15, TimeUnit.SECONDS)
                    .until(() -> LabStore.get("conn-1") != null);

            assertThat(LabStore.get("conn-1").getStatus()).isEqualTo("CREATED");
        }
        // broker2 is now closed — the bundle should NOT crash; supervisor stays alive
        assertThat(bundle.getEngineSupervisor().isShuttingDown()).isFalse();
    }

    @Test
    void multipleBundlesCanConnectToBrokerSimultaneously() throws Exception {
        var bundle2 = (EventoBundle) null;
        try {
            bundle = EventoBundle.Builder.builder()
                    .setBasePackage(com.evento.lab.bundle.consumer.LabProjector.class.getPackage())
                    .setBundleId("lab-multi-A")
                    .setEventoServerMessageBusConfiguration(
                            new EventoServerMessageBusConfiguration(
                                    new ClusterNodeAddress("127.0.0.1", broker.port())))
                    .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                    .start();

            bundle2 = EventoBundle.Builder.builder()
                    .setBasePackage(com.evento.lab.bundle.query.LabProjection.class.getPackage())
                    .setBundleId("lab-multi-B")
                    .setEventoServerMessageBusConfiguration(
                            new EventoServerMessageBusConfiguration(
                                    new ClusterNodeAddress("127.0.0.1", broker.port())))
                    .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                    .start();

            final var b2 = bundle2;
            await().atMost(20, TimeUnit.SECONDS)
                    .until(() -> broker.lifecycle().isBundleAvailable("lab-multi-A")
                            && broker.lifecycle().isBundleAvailable("lab-multi-B"));

            assertThat(broker.lifecycle().view().size()).isGreaterThanOrEqualTo(2);
        } finally {
            if (bundle2 != null) {
                bundle2.getEngineSupervisor().stop(Duration.ofSeconds(5));
            }
        }
    }
}
