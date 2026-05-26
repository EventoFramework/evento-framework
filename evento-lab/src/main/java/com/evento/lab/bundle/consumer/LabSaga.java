package com.evento.lab.bundle.consumer;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.lab.api.event.LabSagaFailEvent;
import com.evento.lab.api.event.OrderCancelledEvent;
import com.evento.lab.api.event.OrderConfirmedEvent;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.api.query.FindOrderRichByIdQuery;

import java.util.concurrent.ExecutionException;

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
    LabSagaState on(OrderConfirmedEvent e, LabSagaState state,
                    QueryGateway qg) throws ExecutionException, InterruptedException {
        // Query enrichment: fetch the rich view to log a full picture at confirmation time.
        var rich = qg.query(new FindOrderRichByIdQuery(e.getOrderId())).get();
        System.out.println("LabSaga confirmed — rich view: " + rich.getData());
        state.setStatus("CONFIRMED");
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId")
    LabSagaState on(OrderCancelledEvent e, LabSagaState state) {
        state.setStatus("CANCELLED");
        return state;
    }

    /** Exercises saga HANDLER-level failure (no interceptor involved). */
    @SagaEventHandler(init = true, associationProperty = "none")
    LabSagaState on(LabSagaFailEvent e) {
        throw new RuntimeException("LabSaga: deliberate saga handler failure");
    }
}
