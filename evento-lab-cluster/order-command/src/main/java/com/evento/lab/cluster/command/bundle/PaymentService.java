package com.evento.lab.cluster.command.bundle;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.lab.cluster.api.command.ProcessPaymentCommand;
import com.evento.lab.cluster.api.event.PaymentProcessedEvent;
import com.evento.lab.cluster.support.FrameworkFeatures;
import com.evento.lab.cluster.support.store.SideEffectStore;

import java.time.Duration;

/**
 * The payment "provider". Its side effect (a row in {@code payments}) is
 * counted, so a saga that runs twice for one order shows up as
 * {@code attempts > 1} rather than being hidden by the upsert.
 *
 * <p>Exactly-once (G5 residual window): the payment row and the command's
 * outcome are written in one transaction ({@code ServiceCommandOutcomes.recordWith},
 * through {@link FrameworkFeatures#recordServiceOutcome} so the pre-fix images still
 * compile). When the broker could not store the {@code PaymentProcessedEvent} (its
 * append failed, or the server died first) and retries the command, the bundle
 * answers from the recorded outcome and this handler does not run again.
 * Without the store (pre-fix images, {@code EVENTO_SERVICE_OUTCOME_STORE=off}) the
 * retry pays a second time: {@code attempts = 2}.
 *
 * <p>Lab hook for S18: an order whose id contains {@value #SLOW_MARKER} answers
 * {@code LAB_PAYMENT_SLOW_MS} (default 4000) after its payment committed, which
 * gives the scenario the time to cut the server off the event store between the
 * handler's side effect and the append.
 */
@Service
public class PaymentService {

    public static final String SLOW_MARKER = "slowpay";
    private static final Duration SLOW = Duration.ofMillis(Long.parseLong(
            System.getenv().getOrDefault("LAB_PAYMENT_SLOW_MS", "4000")));

    private final SideEffectStore sideEffects;

    public PaymentService(SideEffectStore sideEffects) {
        this.sideEffects = sideEffects;
    }

    @CommandHandler
    PaymentProcessedEvent handle(ProcessPaymentCommand cmd) throws Exception {
        var paymentId = "PAY-" + cmd.getOrderId();
        var event = sideEffects.recordPayment(cmd.getOrderId(), paymentId, cmd.getRegion(), (con, attempt) -> {
            var e = new PaymentProcessedEvent(cmd.getOrderId(), paymentId, attempt);
            if (cmd.getRegion() != null && !cmd.getRegion().isBlank()) {
                e.setContext(cmd.getRegion());
            }
            // Same transaction as the payment row; a no-op without a key or a store.
            FrameworkFeatures.recordServiceOutcome(con, e);
            return e;
        });
        if (cmd.getOrderId() != null && cmd.getOrderId().contains(SLOW_MARKER)) {
            Thread.sleep(SLOW);
        }
        return event;
    }
}
