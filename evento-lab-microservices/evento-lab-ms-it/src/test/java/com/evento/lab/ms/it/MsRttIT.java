package com.evento.lab.ms.it;

import com.evento.lab.ms.api.event.OrderCompletedEvent;
import com.evento.lab.ms.api.event.OrderCreatedEvent;
import com.evento.lab.ms.it.support.MsHarness;
import com.evento.lab.ms.observer.observer.MsObservedEvents;
import com.evento.lab.ms.query.store.OrderViewStore;
import com.evento.lab.ms.saga.saga.MsSagaStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for round-trip time (RTT) and stress scenarios.
 * No Docker required.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class MsRttIT {

    private MsHarness harness;

    @BeforeEach
    void setUp() throws Exception {
        OrderViewStore.reset();
        MsObservedEvents.reset();
        MsSagaStore.reset();
        harness = new MsHarness();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) harness.close();
    }

    @Test
    void singleEventRtt_projectorProcessesWithinThreshold() throws Exception {
        harness.withQueryBundle();
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-query"));

        String orderId = "rtt-single-" + UUID.randomUUID();
        long startNs = System.nanoTime();

        harness.eventStore().publish(new OrderCreatedEvent(orderId, "RTT test", 1), orderId);

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> OrderViewStore.get(orderId) != null);

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        System.out.printf("Single event RTT: %d ms%n", elapsedMs);

        assertThat(OrderViewStore.get(orderId)).isNotNull();
        assertThat(elapsedMs).isLessThan(3000L);
    }

    @Test
    void stressTest_100OrdersAllProjected() throws Exception {
        harness.withQueryBundle();
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-query"));

        long startNs = System.nanoTime();
        for (int i = 1; i <= 100; i++) {
            harness.eventStore().publish(new OrderCreatedEvent("stress-" + i, "Stress Order " + i, i), "stress-" + i);
        }

        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> {
                    long count = OrderViewStore.getAll().stream()
                            .filter(v -> v.getOrderId().startsWith("stress-"))
                            .count();
                    return count >= 100;
                });

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        double throughput = 100_000.0 / elapsedMs;
        System.out.printf("100 orders processed in %d ms (%.1f orders/sec)%n", elapsedMs, throughput);

        for (int i = 1; i <= 100; i++) {
            assertThat(OrderViewStore.get("stress-" + i))
                    .as("Order stress-%d should be projected", i)
                    .isNotNull();
        }
    }

    @Test
    void concurrentBundlesFanOut_allConsumersReceive() throws Exception {
        harness.withQueryBundle().withObserverBundle();

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-query")
                        && harness.broker().lifecycle().isBundleAvailable("lab-ms-observer"));

        for (int i = 1; i <= 20; i++) {
            harness.eventStore().publish(new OrderCreatedEvent("fanout-" + i, "Fanout Order " + i, i), "fanout-" + i);
        }

        // All events appear in view store (projector)
        await().atMost(25, TimeUnit.SECONDS)
                .until(() -> {
                    long count = OrderViewStore.getAll().stream()
                            .filter(v -> v.getOrderId().startsWith("fanout-"))
                            .count();
                    return count >= 20;
                });

        // All events appear in observed events (observer)
        await().atMost(25, TimeUnit.SECONDS)
                .until(() -> {
                    long count = MsObservedEvents.getAll().stream()
                            .filter(e -> e.startsWith("created:fanout-"))
                            .count();
                    return count >= 20;
                });

        // Assert all 20 fanout orders are in both stores
        for (int i = 1; i <= 20; i++) {
            assertThat(OrderViewStore.get("fanout-" + i))
                    .as("fanout-%d should be in view store", i)
                    .isNotNull();
        }
        assertThat(MsObservedEvents.getAll().stream()
                .filter(e -> e.startsWith("created:fanout-"))
                .count()).isGreaterThanOrEqualTo(20);
    }
}
