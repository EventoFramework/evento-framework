package com.evento.lab.ms.saga.saga;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.lab.ms.api.command.CancelOrderCommand;
import com.evento.lab.ms.api.command.ConfirmOrderCommand;
import com.evento.lab.ms.api.command.OpenPaymentIntentCommand;
import com.evento.lab.ms.api.event.OrderCompletedEvent;
import com.evento.lab.ms.api.event.PaymentStatusChangedEvent;

@Saga(version = 1)
public class OrderSaga {

    @SagaEventHandler(init = true, associationProperty = "orderId")
    OrderSagaState on(OrderCompletedEvent e, CommandGateway cg, QueryGateway qg) throws Exception {
        var state = new OrderSagaState();
        state.setAssociation("orderId", e.getOrderId());
        state.setOrderId(e.getOrderId());
        String paymentIntentId = "PI-" + e.getOrderId() + "-" + System.currentTimeMillis();
        state.setPaymentIntentId(paymentIntentId);
        state.setPhase("PAYMENT_PENDING");
        MsSagaStore.record(e.getOrderId(), "PAYMENT_PENDING");
        cg.send(new OpenPaymentIntentCommand(e.getOrderId(), paymentIntentId)).get();
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId")
    OrderSagaState on(PaymentStatusChangedEvent e, OrderSagaState state, CommandGateway cg) throws Exception {
        if ("SUCCESS".equals(e.getStatus())) {
            state.setPhase("PAYMENT_SUCCESS");
            MsSagaStore.record(state.getOrderId(), "PAYMENT_SUCCESS");
            cg.send(new ConfirmOrderCommand(state.getOrderId())).get();
        } else {
            state.setPhase("PAYMENT_FAILED");
            MsSagaStore.record(state.getOrderId(), "PAYMENT_FAILED");
            cg.send(new CancelOrderCommand(state.getOrderId(), "payment-failed")).get();
        }
        return state;
    }
}
