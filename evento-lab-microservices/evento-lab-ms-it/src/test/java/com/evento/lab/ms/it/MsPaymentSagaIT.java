package com.evento.lab.ms.it;

import com.evento.lab.ms.api.event.OrderCompletedEvent;
import com.evento.lab.ms.api.event.PaymentStatusChangedEvent;
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
 * Integration tests for the payment saga flow.
 * The saga opens a payment intent on OrderCompletedEvent, then confirms or cancels the order
 * based on PaymentStatusChangedEvent. Both saga and command bundles must run together.
 * No Docker required.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class MsPaymentSagaIT {

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
    void paymentSuccess_sagaOpensIntentAndConfirmsOrder() throws Exception {
        harness.withSagaBundle().withCommandBundle();

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-saga")
                        && harness.broker().lifecycle().isBundleAvailable("lab-ms-command"));

        harness.eventStore().publishServiceEvent(new OrderCompletedEvent("pay-success-1"));
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> "PAYMENT_PENDING".equals(MsSagaStore.getStatus("pay-success-1")));

        harness.eventStore().publishServiceEvent(new PaymentStatusChangedEvent("pay-success-1", "PI-pay-success-1", "SUCCESS"));
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> "PAYMENT_SUCCESS".equals(MsSagaStore.getStatus("pay-success-1")));

        assertThat(MsSagaStore.getStatus("pay-success-1")).isEqualTo("PAYMENT_SUCCESS");
    }

    @Test
    void paymentFailure_sagaOpensIntentAndCancelsOrder() throws Exception {
        harness.withSagaBundle().withCommandBundle();

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-saga")
                        && harness.broker().lifecycle().isBundleAvailable("lab-ms-command"));

        harness.eventStore().publishServiceEvent(new OrderCompletedEvent("pay-fail-1"));
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> "PAYMENT_PENDING".equals(MsSagaStore.getStatus("pay-fail-1")));

        harness.eventStore().publishServiceEvent(new PaymentStatusChangedEvent("pay-fail-1", "PI-pay-fail-1", "FAILED"));
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> "PAYMENT_FAILED".equals(MsSagaStore.getStatus("pay-fail-1")));

        assertThat(MsSagaStore.getStatus("pay-fail-1")).isEqualTo("PAYMENT_FAILED");
    }
}
