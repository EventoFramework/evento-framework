package com.evento.lab.multievent;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.messaging.payload.DomainEvents;

import java.util.ArrayList;

/** One decision, several events: one reservation per item, the check-out, a coupon only sometimes. */
@Aggregate
public class BasketAggregate {

    @AggregateCommandHandler(init = true)
    BasketCreated handle(CreateBasket c) {
        return new BasketCreated(c.getBasketId());
    }

    @EventSourcingHandler
    BasketState on(BasketCreated e) {
        return new BasketState();
    }

    @AggregateCommandHandler(produces = {ItemReserved.class, BasketCheckedOut.class, CouponUsed.class})
    DomainEvents handle(CheckOut c, BasketState state) {
        var events = new ArrayList<DomainEvent>();
        for (int i = 0; i < c.getItems(); i++) events.add(new ItemReserved(c.getBasketId(), i));
        events.add(new BasketCheckedOut(c.getBasketId()));
        if (c.isCoupon()) events.add(new CouponUsed(c.getBasketId()));
        return DomainEvents.of(events);
    }

    /** A handler bug: an event outside its declaration. */
    @AggregateCommandHandler(produces = ItemReserved.class)
    DomainEvents handle(Sneak c, BasketState state) {
        return DomainEvents.of(new ItemReserved(c.getBasketId(), 0), new BasketCheckedOut(c.getBasketId()));
    }
}
