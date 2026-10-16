package com.evento.test.shop;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.common.modeling.messaging.payload.Query;

@Saga(version = 1)
public class PaymentSaga {

    @SagaEventHandler(init = true, associationProperty = "orderId")
    PaymentState on(OrderCreated e) {
        var s = new PaymentState();
        s.setAssociation("orderId", e.orderId);
        return s;
    }

    @SagaEventHandler(associationProperty = "orderId")
    void on(ItemAdded e, PaymentState state, QueryGateway queries) throws Exception {
        state.total += queries.query(new FindPrice(e.item)).get().getData().price;
    }

    @SagaEventHandler(associationProperty = "orderId")
    PaymentState on(OrderPaid e, PaymentState state, CommandGateway commands) throws Exception {
        commands.send(new Charge(e.orderId, state.total)).get();
        commands.send(new Notify(e.orderId)).get();
        state.setEnded(true);
        return state;
    }
}
