package com.evento.test.broker.demo;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;

@Aggregate
public class OrderAggregate {
    @AggregateCommandHandler(init = true)
    OrderCreated handle(CreateOrder c) {
        return new OrderCreated(c.orderId);
    }

    @EventSourcingHandler
    OrderState on(OrderCreated e) {
        return new OrderState();
    }
}
