package com.evento.lab.ms.saga.saga;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.lab.ms.api.event.OrderCancelledEvent;
import com.evento.lab.ms.api.event.OrderConfirmedEvent;
import com.evento.lab.ms.api.event.OrderCreatedEvent;

@Saga(version = 1)
public class OrderSaga {

    @SagaEventHandler(init = true, associationProperty = "orderId")
    OrderSagaState on(OrderCreatedEvent e, CommandGateway cg, QueryGateway qg) {
        var state = new OrderSagaState();
        state.setAssociation("orderId", e.getOrderId());
        state.setOrderId(e.getOrderId());
        state.setStatus("CREATED");
        MsSagaStore.record(e.getOrderId(), "CREATED");
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId")
    OrderSagaState on(OrderConfirmedEvent e, OrderSagaState state) {
        state.setStatus("CONFIRMED");
        MsSagaStore.record(state.getOrderId(), "CONFIRMED");
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId")
    OrderSagaState on(OrderCancelledEvent e, OrderSagaState state) {
        state.setStatus("CANCELLED");
        MsSagaStore.record(state.getOrderId(), "CANCELLED");
        return state;
    }
}
