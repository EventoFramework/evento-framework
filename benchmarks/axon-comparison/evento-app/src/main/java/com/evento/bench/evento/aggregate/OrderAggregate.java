package com.evento.bench.evento.aggregate;

import com.evento.bench.evento.api.AddItem;
import com.evento.bench.evento.api.CreateOrder;
import com.evento.bench.evento.api.ItemAdded;
import com.evento.bench.evento.api.OrderCreated;
import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;

/** The benchmark order, with the framework defaults (no snapshots). */
@Aggregate
public class OrderAggregate {

    @AggregateCommandHandler(init = true)
    OrderCreated handle(CreateOrder command) {
        return new OrderCreated(command.getOrderId());
    }

    @AggregateCommandHandler
    ItemAdded handle(AddItem command, OrderState state) {
        if (state == null) throw new IllegalStateException("order " + command.getOrderId() + " does not exist");
        return new ItemAdded(command.getOrderId(), command.getSku(), command.getQuantity());
    }

    @EventSourcingHandler
    OrderState on(OrderCreated event) {
        return new OrderState();
    }

    @EventSourcingHandler
    OrderState on(ItemAdded event, OrderState state) {
        state.setItems(state.getItems() + 1);
        return state;
    }
}
