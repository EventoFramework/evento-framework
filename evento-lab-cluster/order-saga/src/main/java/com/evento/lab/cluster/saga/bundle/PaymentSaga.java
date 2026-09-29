package com.evento.lab.cluster.saga.bundle;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.lab.cluster.api.command.ProcessPaymentCommand;
import com.evento.lab.cluster.api.event.OrderCompletedEvent;
import com.evento.lab.cluster.api.event.PaymentProcessedEvent;

/**
 * OrderCompleted -> ProcessPayment -> PaymentProcessed -> end.
 *
 * <p>One class for every region: bundle {@code order-saga} is deployed once
 * per cell with that cell's regions in {@code EVENTO_CONTEXTS}, which starts
 * one saga engine per region ({@code order-saga_PaymentSaga_1_EUROPA}, ...).
 * A region's payments therefore run next to the region, and a cell outage
 * stalls only that cell's sagas. A second deployment with the same contexts
 * and the same consumer-state database would compete for the lanes; a lock
 * handover can then replay an event, and a second payment shows up as
 * {@code payments.attempts > 1}.
 *
 * <p>No idempotency code here on purpose: on a framework with G5 the saga's
 * gateway derives the key of {@code ProcessPaymentCommand} from (consumer id,
 * sequence number of the {@code OrderCompletedEvent}, ProcessPaymentCommand, 0). When the event is
 * redelivered (a {@code BrokerChangedException} or timeout failed the handler,
 * a lane changed owner before the checkpoint) the retry carries the same key
 * and the broker answers with the stored {@code PaymentProcessedEvent} instead
 * of charging again (N14). What it cannot cover is a payment that ran while its
 * event was not stored (S07's partition): that retry runs the handler again.
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
