package com.evento.lab.bundle.consumer;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.lab.api.event.OrderCancelledEvent;
import com.evento.lab.api.event.OrderConfirmedEvent;
import com.evento.lab.api.event.OrderCreatedEvent;

@Saga(version = 1)
public class LabSaga {

    @SagaEventHandler(init = true, associationProperty = "orderId")
    LabSagaState on(OrderCreatedEvent e, CommandGateway cg, QueryGateway qg) {
        var state = new LabSagaState();
        state.setAssociation("orderId", e.getOrderId());
        state.setOrderId(e.getOrderId());
        state.setStatus("CREATED");
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId")
    LabSagaState on(OrderConfirmedEvent e, LabSagaState state) {
        state.setStatus("CONFIRMED");
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId")
    LabSagaState on(OrderCancelledEvent e, LabSagaState state) {
        state.setStatus("CANCELLED");
        return state;
    }
}
