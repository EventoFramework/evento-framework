package com.evento.lab.ms.it;

import com.evento.lab.ms.api.event.OrderCreatedEvent;
import com.evento.lab.ms.it.support.MsHarness;
import com.evento.lab.ms.observer.observer.MsObservedEvents;
import com.evento.lab.ms.query.store.OrderViewStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for multi-bundle consumer scenarios using in-memory state stores.
 * No Docker required.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class MsConsumerLifecycleIT {

    private MsHarness harness;

    @BeforeEach
    void setUp() throws Exception {
        OrderViewStore.reset();
        MsObservedEvents.reset();
        harness = new MsHarness();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) harness.close();
    }

    @Test
    void allThreeConsumerBundlesReceivePublishedEvents() throws Exception {
        harness.withQueryBundle().withSagaBundle().withObserverBundle();

        await().atMost(20, TimeUnit.SECONDS).until(() ->
                harness.broker().lifecycle().isBundleAvailable("lab-ms-query") &&
                harness.broker().lifecycle().isBundleAvailable("lab-ms-saga") &&
                harness.broker().lifecycle().isBundleAvailable("lab-ms-observer"));

        harness.eventStore().publish(new OrderCreatedEvent("ms-order-1", "desc", 3), "ms-order-1");

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> OrderViewStore.get("ms-order-1") != null);
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> MsObservedEvents.getAll().contains("created:ms-order-1"));

        assertThat(OrderViewStore.get("ms-order-1").getStatus()).isEqualTo("CREATED");
        assertThat(MsObservedEvents.getAll()).contains("created:ms-order-1");
    }

    @Test
    void queryBundleProjectsMultipleEventsCorrectly() throws Exception {
        harness.withQueryBundle();
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-query"));

        for (int i = 1; i <= 5; i++) {
            harness.eventStore().publish(new OrderCreatedEvent("proj-" + i, "d" + i, i), "proj-" + i);
        }

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> OrderViewStore.getAll().size() >= 5);

        for (int i = 1; i <= 5; i++) {
            var view = OrderViewStore.get("proj-" + i);
            assertThat(view).isNotNull();
            assertThat(view.getQuantity()).isEqualTo(i);
            assertThat(view.getStatus()).isEqualTo("CREATED");
        }
    }

    @Test
    void observerAndProjectorReceiveSameEventIndependently() throws Exception {
        harness.withQueryBundle().withObserverBundle();
        await().atMost(15, TimeUnit.SECONDS).until(() ->
                harness.broker().lifecycle().isBundleAvailable("lab-ms-query") &&
                harness.broker().lifecycle().isBundleAvailable("lab-ms-observer"));

        harness.eventStore().publish(new OrderCreatedEvent("shared-1", "s", 1), "shared-1");

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> OrderViewStore.get("shared-1") != null
                        && MsObservedEvents.getAll().contains("created:shared-1"));

        assertThat(OrderViewStore.get("shared-1").getStatus()).isEqualTo("CREATED");
        assertThat(MsObservedEvents.getAll()).contains("created:shared-1");
    }
}
