package com.evento.test.shop;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.common.modeling.messaging.payload.DomainEvents;

@Aggregate
public class OrderAggregate {

    @AggregateCommandHandler(init = true)
    OrderCreated handle(CreateOrder c) {
        if (c.budget <= 0) throw new IllegalArgumentException("budget must be positive");
        return new OrderCreated(c.orderId, c.budget);
    }

    @EventSourcingHandler
    OrderState on(OrderCreated e) {
        var s = new OrderState();
        s.budget = e.budget;
        return s;
    }

    @AggregateCommandHandler
    ItemAdded handle(AddItem c, OrderState state) {
        if (state.spent + c.price > state.budget) throw new IllegalStateException("over budget");
        return new ItemAdded(c.orderId, c.item, c.price);
    }

    @EventSourcingHandler
    void on(ItemAdded e, OrderState state) {
        state.items.add(e.item);
        state.spent += e.price;
    }

    @AggregateCommandHandler
    OrderDeleted handle(DeleteOrder c, OrderState state) {
        return new OrderDeleted(c.orderId);
    }

    @EventSourcingHandler
    void on(OrderDeleted e, OrderState state) {
        state.setDeleted(true);
    }

    @AggregateCommandHandler(produces = ItemAdded.class)
    DomainEvents handle(AddPair c, OrderState state) {
        if (state.spent + 2 * c.price > state.budget) throw new IllegalStateException("over budget");
        return DomainEvents.of(new ItemAdded(c.orderId, c.first, c.price), new ItemAdded(c.orderId, c.second, c.price));
    }

    @AggregateCommandHandler(produces = {OrderDeleted.class, ItemAdded.class})
    DomainEvents handle(DeleteThenAdd c, OrderState state) {
        return DomainEvents.of(new OrderDeleted(c.orderId), new ItemAdded(c.orderId, "late", 1));
    }

    @AggregateCommandHandler(produces = ItemAdded.class)
    DomainEvents handle(AddThenDelete c, OrderState state) {
        return DomainEvents.of(new ItemAdded(c.orderId, "last", 1), new OrderDeleted(c.orderId));
    }
}
