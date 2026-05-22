package com.evento.lab.ms.it;

import com.evento.lab.ms.api.event.OrderCreatedEvent;
import com.evento.lab.ms.it.support.MsHarness;
import com.evento.lab.ms.query.store.OrderViewStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for multi-context parallel consumer scenarios.
 * Two projector engines — one for "IT" context, one for "UK" context — each with their own checkpoint.
 * No Docker required.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class MsMultiContextIT {

    private MsHarness harness;

    @BeforeEach
    void setUp() throws Exception {
        OrderViewStore.reset();
        harness = new MsHarness();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) harness.close();
    }

    @Test
    void parallelContextConsumers_ITEventsOnlyReachITProjector() throws Exception {
        harness.withQueryBundleForContext("IT").withQueryBundleForContext("UK");

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-query-it")
                        && harness.broker().lifecycle().isBundleAvailable("lab-ms-query-uk"));

        // Publish 2 events with context "IT" and 2 events with context "UK"
        harness.eventStore().publishWithContext(new OrderCreatedEvent("it-1", "IT Order 1", 1, "IT"), "it-1", "IT");
        harness.eventStore().publishWithContext(new OrderCreatedEvent("it-2", "IT Order 2", 1, "IT"), "it-2", "IT");
        harness.eventStore().publishWithContext(new OrderCreatedEvent("uk-1", "UK Order 1", 1, "UK"), "uk-1", "UK");
        harness.eventStore().publishWithContext(new OrderCreatedEvent("uk-2", "UK Order 2", 1, "UK"), "uk-2", "UK");

        // Each context consumer processes its own events — total 4 views
        await().atMost(25, TimeUnit.SECONDS)
                .until(() -> {
                    var it1 = OrderViewStore.get("it-1");
                    var it2 = OrderViewStore.get("it-2");
                    var uk1 = OrderViewStore.get("uk-1");
                    var uk2 = OrderViewStore.get("uk-2");
                    return it1 != null && it2 != null && uk1 != null && uk2 != null;
                });

        assertThat(OrderViewStore.get("it-1")).isNotNull();
        assertThat(OrderViewStore.get("it-1").getContext()).isEqualTo("IT");
        assertThat(OrderViewStore.get("it-2")).isNotNull();
        assertThat(OrderViewStore.get("uk-1")).isNotNull();
        assertThat(OrderViewStore.get("uk-1").getContext()).isEqualTo("UK");
        assertThat(OrderViewStore.get("uk-2")).isNotNull();
    }

    @Test
    void contextBundlesProcessEventsInParallel() throws Exception {
        harness.withQueryBundleForContext("IT").withQueryBundleForContext("UK");

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-query-it")
                        && harness.broker().lifecycle().isBundleAvailable("lab-ms-query-uk"));

        // Interleave IT and UK events
        for (int i = 1; i <= 3; i++) {
            harness.eventStore().publishWithContext(
                    new OrderCreatedEvent("ctx-it-" + i, "IT Order " + i, i, "IT"), "ctx-it-" + i, "IT");
            harness.eventStore().publishWithContext(
                    new OrderCreatedEvent("ctx-uk-" + i, "UK Order " + i, i, "UK"), "ctx-uk-" + i, "UK");
        }

        await().atMost(25, TimeUnit.SECONDS)
                .until(() -> {
                    long itCount = OrderViewStore.getAll().stream()
                            .filter(v -> v.getOrderId().startsWith("ctx-it-")).count();
                    long ukCount = OrderViewStore.getAll().stream()
                            .filter(v -> v.getOrderId().startsWith("ctx-uk-")).count();
                    return itCount >= 3 && ukCount >= 3;
                });

        for (int i = 1; i <= 3; i++) {
            assertThat(OrderViewStore.get("ctx-it-" + i)).isNotNull();
            assertThat(OrderViewStore.get("ctx-it-" + i).getContext()).isEqualTo("IT");
            assertThat(OrderViewStore.get("ctx-uk-" + i)).isNotNull();
            assertThat(OrderViewStore.get("ctx-uk-" + i).getContext()).isEqualTo("UK");
        }
    }
}
