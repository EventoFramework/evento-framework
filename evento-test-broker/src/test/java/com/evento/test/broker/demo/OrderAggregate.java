package com.evento.test.broker.demo;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.common.modeling.messaging.payload.DomainEvents;

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

    @AggregateCommandHandler(init = true, produces = {OrderCreated.class, NoteAdded.class})
    DomainEvents handle(OpenOrder c) {
        return DomainEvents.of(new OrderCreated(c.orderId), new NoteAdded(c.orderId, c.note));
    }
}
