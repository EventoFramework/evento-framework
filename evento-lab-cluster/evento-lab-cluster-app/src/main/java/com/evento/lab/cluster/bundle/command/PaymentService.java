package com.evento.lab.cluster.bundle.command;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.lab.cluster.api.command.ProcessPaymentCommand;
import com.evento.lab.cluster.api.event.PaymentProcessedEvent;
import com.evento.lab.cluster.store.SideEffectStore;

/**
 * The payment "provider". Its side effect (a row in {@code payments}) is
 * counted, so a saga that runs twice for one order shows up as
 * {@code attempts > 1} rather than being hidden by the upsert.
 */
@Service
public class PaymentService {

    private final SideEffectStore sideEffects;

    public PaymentService(SideEffectStore sideEffects) {
        this.sideEffects = sideEffects;
    }

    @CommandHandler
    PaymentProcessedEvent handle(ProcessPaymentCommand cmd) throws Exception {
        var paymentId = "PAY-" + cmd.getOrderId();
        var attempt = sideEffects.recordPayment(cmd.getOrderId(), paymentId, cmd.getRegion());
        var event = new PaymentProcessedEvent(cmd.getOrderId(), paymentId, attempt);
        if (cmd.getRegion() != null && !cmd.getRegion().isBlank()) {
            event.setContext(cmd.getRegion());
        }
        return event;
    }
}
