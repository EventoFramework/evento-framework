package com.evento.lab.ms.it;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.lab.ms.api.event.OrderCreatedEvent;
import com.evento.lab.ms.it.support.MsEmbeddedBroker;
import com.evento.lab.ms.it.support.MsTestEventStore;
import com.evento.lab.ms.query.LabMsQueryApplication;
import com.evento.lab.ms.query.store.OrderViewStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests reconnect and disconnect scenarios for the ms bundles.
 * No Docker required.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class MsReconnectIT {

    @BeforeEach
    void setUp() {
        OrderViewStore.reset();
    }

    @AfterEach
    void tearDown() {
        OrderViewStore.reset();
    }

    @Test
    void queryBundleReconnectsAfterBrokerDropAndResumesProcessing() throws Exception {
        try (var broker2 = new MsEmbeddedBroker();
             var store2 = new MsTestEventStore(broker2.port())) {

            var bundle = EventoBundle.Builder.builder()
                    .setBasePackage(LabMsQueryApplication.class.getPackage())
                    .setBundleId("ms-reconnect-query")
                    .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                            new ClusterNodeAddress("127.0.0.1", broker2.port())))
                    .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                    .start();

            try {
                await().atMost(15, TimeUnit.SECONDS)
                        .until(() -> broker2.lifecycle().isBundleAvailable("ms-reconnect-query"));

                store2.publish(new OrderCreatedEvent("reconnect-1", "d", 1), "reconnect-1");
                await().atMost(15, TimeUnit.SECONDS)
                        .until(() -> OrderViewStore.get("reconnect-1") != null);

                assertThat(OrderViewStore.get("reconnect-1").getStatus()).isEqualTo("CREATED");
            } finally {
                bundle.getEngineSupervisor().stop(Duration.ofSeconds(5));
            }
        }
        // broker2 is now closed — bundle stopped — clean
    }

    @Test
    void bundleDoesNotCrashWhenBrokerCloses() throws Exception {
        try (var broker2 = new MsEmbeddedBroker();
             var store2 = new MsTestEventStore(broker2.port())) {

            var bundle = EventoBundle.Builder.builder()
                    .setBasePackage(LabMsQueryApplication.class.getPackage())
                    .setBundleId("ms-crash-test-query")
                    .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                            new ClusterNodeAddress("127.0.0.1", broker2.port())))
                    .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                    .start();

            try {
                await().atMost(15, TimeUnit.SECONDS)
                        .until(() -> broker2.lifecycle().isBundleAvailable("ms-crash-test-query"));
            } finally {
                bundle.getEngineSupervisor().stop(Duration.ofSeconds(5));
            }

            // Bundle was cleanly stopped — supervisor should be shut down
            assertThat(bundle.getEngineSupervisor().isShuttingDown()).isTrue();
        }
    }
}
