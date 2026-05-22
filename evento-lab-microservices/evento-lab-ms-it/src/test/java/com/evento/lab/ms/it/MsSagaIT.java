package com.evento.lab.ms.it;

import com.evento.lab.ms.api.event.OrderCancelledEvent;
import com.evento.lab.ms.api.event.OrderConfirmedEvent;
import com.evento.lab.ms.api.event.OrderCreatedEvent;
import com.evento.lab.ms.it.support.MsHarness;
import com.evento.lab.ms.saga.saga.MsSagaStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for saga lifecycle scenarios using in-memory state stores.
 * No Docker required.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class MsSagaIT {

    private MsHarness harness;

    @BeforeEach
    void setUp() throws Exception {
        MsSagaStore.reset();
        harness = new MsHarness();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) harness.close();
    }

    @Test
    void sagaHappyPath_createdThenConfirmed() throws Exception {
        harness.withSagaBundle();
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-saga"));

        harness.eventStore().publish(new OrderCreatedEvent("saga-1", "d", 1), "saga-1");
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> MsSagaStore.getStatus("saga-1") != null);
        assertThat(MsSagaStore.getStatus("saga-1")).isEqualTo("CREATED");

        harness.eventStore().publishServiceEvent(new OrderConfirmedEvent("saga-1"));
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> "CONFIRMED".equals(MsSagaStore.getStatus("saga-1")));
        assertThat(MsSagaStore.getStatus("saga-1")).isEqualTo("CONFIRMED");
    }

    @Test
    void sagaCompensation_createdThenCancelled() throws Exception {
        harness.withSagaBundle();
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-saga"));

        harness.eventStore().publish(new OrderCreatedEvent("saga-cancel-1", "d", 1), "saga-cancel-1");
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> MsSagaStore.getStatus("saga-cancel-1") != null);

        harness.eventStore().publishServiceEvent(new OrderCancelledEvent("saga-cancel-1", "test"));
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> "CANCELLED".equals(MsSagaStore.getStatus("saga-cancel-1")));
        assertThat(MsSagaStore.getStatus("saga-cancel-1")).isEqualTo("CANCELLED");
    }
}
