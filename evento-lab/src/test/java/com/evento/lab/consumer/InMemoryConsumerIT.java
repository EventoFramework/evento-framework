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
 * Integration tests for the Order lifecycle with in-memory consumer state stores.
 *
 * <p>Wires a real {@link EmbeddedBroker} + {@link TestEventStoreBundleClient} +
 * {@link EventoBundle} over loopback TCP so all three layers exercise real code.
 * State stores are the default in-memory implementations so tests run without Docker.
 *
 * <p>Each test waits for the bundle to be fully available (projectors at head,
 * observers started, bundle enabled) before running assertions. This ensures
 * the async startup thread completes before {@code tearDown()} can interrupt it.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class InMemoryConsumerIT {

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

    /**
     * Start bundle and block until it is fully enabled (projectors at head,
     * saga+observer engines started). This avoids race conditions where tearDown
     * interrupts the async startup thread.
     */
    private EventoBundle startBundleAndWait() throws Exception {
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(com.evento.lab.bundle.consumer.LabProjector.class.getPackage())
                .setBundleId("lab-bundle")
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();
        // Wait until all projectors have caught up to head and the bundle is enabled
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> broker.lifecycle().isBundleAvailable("lab-bundle"));
        return bundle;
    }

    @Test
    void projectorProcessesPublishedEvents() throws Exception {
        // Publish 5 orders into the store BEFORE the bundle starts
        for (int i = 1; i <= 5; i++) {
            eventStore.publish(new OrderCreatedEvent("order-" + i, "desc-" + i, i), "order-" + i);
        }

        startBundleAndWait();

        // Projector should pick up all 5 events
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> LabStore.getAll().size() == 5);

        for (int i = 1; i <= 5; i++) {
            var view = LabStore.get("order-" + i);
            assertThat(view).as("order-%d should be in store", i).isNotNull();
            assertThat(view.getDescription()).isEqualTo("desc-" + i);
            assertThat(view.getQuantity()).isEqualTo(i);
            assertThat(view.getStatus()).isEqualTo("CREATED");
        }
    }

    @Test
    void observerRecordsLiveEvents() throws Exception {
        // Start bundle first (no pre-published events) so projectors reach head immediately
        // Then wait until bundle is enabled (observer is running)
        startBundleAndWait();

        // Now publish — observer is running and will pick these up
        eventStore.publish(new OrderCreatedEvent("o1", "first", 1), "o1");
        eventStore.publish(new OrderCreatedEvent("o2", "second", 2), "o2");

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> LabStore.getObservedEvents().size() >= 2);

        assertThat(LabStore.getObservedEvents()).contains("created:o1", "created:o2");
    }

    @Test
    void livePublishIsPickedUpByRunningProjector() throws Exception {
        startBundleAndWait();

        // Now publish events while the bundle is running
        for (int i = 1; i <= 3; i++) {
            eventStore.publish(new OrderCreatedEvent("live-" + i, "live-desc-" + i, i * 10), "live-" + i);
        }

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> LabStore.getAll().size() >= 3);

        for (int i = 1; i <= 3; i++) {
            var view = LabStore.get("live-" + i);
            assertThat(view).as("live-%d should be in store", i).isNotNull();
            assertThat(view.getQuantity()).isEqualTo(i * 10);
        }
    }
}
