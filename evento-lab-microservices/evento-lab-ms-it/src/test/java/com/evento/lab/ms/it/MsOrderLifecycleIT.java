package com.evento.lab.ms.it;

import com.evento.lab.ms.api.event.OrderCompletedEvent;
import com.evento.lab.ms.api.event.OrderCreatedEvent;
import com.evento.lab.ms.api.event.OrderItemAddedEvent;
import com.evento.lab.ms.api.event.OrderItemRemovedEvent;
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
 * Integration tests for full order lifecycle (create, add items, remove item, complete).
 * Uses direct event publishing to simulate command execution results.
 * No Docker required.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class MsOrderLifecycleIT {

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
    void fullOrderLifecycle_createAddItemsRemoveItemComplete() throws Exception {
        harness.withQueryBundle();
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-query"));

        // Create order
        harness.eventStore().publish(new OrderCreatedEvent("lc-order-1", "Test Order", 1), "lc-order-1");
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> OrderViewStore.get("lc-order-1") != null);
        assertThat(OrderViewStore.get("lc-order-1").getStatus()).isEqualTo("CREATED");

        // Add two items
        harness.eventStore().publish(new OrderItemAddedEvent("lc-order-1", "item-A", "Widget A", 9.99, 2), "lc-order-1");
        harness.eventStore().publish(new OrderItemAddedEvent("lc-order-1", "item-B", "Widget B", 4.99, 1), "lc-order-1");
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> OrderViewStore.get("lc-order-1") != null
                        && OrderViewStore.get("lc-order-1").getItems().size() == 2);
        assertThat(OrderViewStore.get("lc-order-1").getItems()).hasSize(2);

        // Remove one item
        harness.eventStore().publish(new OrderItemRemovedEvent("lc-order-1", "item-A"), "lc-order-1");
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> OrderViewStore.get("lc-order-1").getItems().size() == 1);
        assertThat(OrderViewStore.get("lc-order-1").getItems()).hasSize(1);
        assertThat(OrderViewStore.get("lc-order-1").getItems().get(0).getItemId()).isEqualTo("item-B");

        // Complete order
        harness.eventStore().publishServiceEvent(new OrderCompletedEvent("lc-order-1"));
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> "COMPLETED".equals(OrderViewStore.get("lc-order-1").getStatus()));
        assertThat(OrderViewStore.get("lc-order-1").getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void multipleOrdersProjectedIndependently() throws Exception {
        harness.withQueryBundle();
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-query"));

        // Publish 3 orders each with different items
        harness.eventStore().publish(new OrderCreatedEvent("multi-order-1", "Order One", 1), "multi-order-1");
        harness.eventStore().publish(new OrderCreatedEvent("multi-order-2", "Order Two", 2), "multi-order-2");
        harness.eventStore().publish(new OrderCreatedEvent("multi-order-3", "Order Three", 3), "multi-order-3");

        harness.eventStore().publish(new OrderItemAddedEvent("multi-order-1", "item-1a", "Item 1A", 10.0, 1), "multi-order-1");
        harness.eventStore().publish(new OrderItemAddedEvent("multi-order-2", "item-2a", "Item 2A", 20.0, 2), "multi-order-2");
        harness.eventStore().publish(new OrderItemAddedEvent("multi-order-2", "item-2b", "Item 2B", 25.0, 1), "multi-order-2");
        harness.eventStore().publish(new OrderItemAddedEvent("multi-order-3", "item-3a", "Item 3A", 30.0, 3), "multi-order-3");

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> OrderViewStore.getAll().stream()
                        .filter(v -> v.getOrderId().startsWith("multi-order-"))
                        .count() >= 3);

        // Verify all 3 orders are projected correctly
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> {
                    var o1 = OrderViewStore.get("multi-order-1");
                    var o2 = OrderViewStore.get("multi-order-2");
                    var o3 = OrderViewStore.get("multi-order-3");
                    return o1 != null && o1.getItems().size() == 1
                            && o2 != null && o2.getItems().size() == 2
                            && o3 != null && o3.getItems().size() == 1;
                });

        var o1 = OrderViewStore.get("multi-order-1");
        var o2 = OrderViewStore.get("multi-order-2");
        var o3 = OrderViewStore.get("multi-order-3");

        assertThat(o1).isNotNull();
        assertThat(o1.getItems()).hasSize(1);
        assertThat(o1.getItems().get(0).getItemId()).isEqualTo("item-1a");

        assertThat(o2).isNotNull();
        assertThat(o2.getItems()).hasSize(2);

        assertThat(o3).isNotNull();
        assertThat(o3.getItems()).hasSize(1);
        assertThat(o3.getItems().get(0).getQuantity()).isEqualTo(3);
    }
}
