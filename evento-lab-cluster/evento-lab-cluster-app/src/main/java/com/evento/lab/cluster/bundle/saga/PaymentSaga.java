package com.evento.lab.cluster.bundle.saga;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.lab.cluster.api.command.ProcessPaymentCommand;
import com.evento.lab.cluster.api.event.OrderCompletedEvent;
import com.evento.lab.cluster.api.event.PaymentProcessedEvent;

/**
 * OrderCompleted -> ProcessPayment -> PaymentProcessed -> end. Runs in bundle
 * {@code cluster-saga} in two cells (EU and APAC), so the two copies compete
 * for the one saga lane and a lock handover (S7, S9) can replay an event: a
 * second payment then shows up as {@code payments.attempts > 1}.
 */
@Saga(version = 1)
public class PaymentSaga {

    @SagaEventHandler(init = true, associationProperty = "orderId")
    PaymentSagaState on(OrderCompletedEvent e, CommandGateway commandGateway) throws Exception {
        var state = new PaymentSagaState();
        state.setAssociation("orderId", e.getOrderId());
        state.setOrderId(e.getOrderId());
        state.setRegion(e.getContext());
        state.setPhase("PAYMENT_REQUESTED");
        commandGateway.send(new ProcessPaymentCommand(e.getOrderId(), e.getContext())).get();
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId")
    PaymentSagaState on(PaymentProcessedEvent e, PaymentSagaState state) {
        state.setPhase("PAID");
        state.setEnded(true);
        return state;
    }
}
