package com.evento.lab.ms.command.aggregate;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.lab.ms.api.command.CreateOrderCommand;
import com.evento.lab.ms.api.event.OrderCreatedEvent;

@Aggregate(snapshotFrequency = 5)
public class OrderAggregate {

    @AggregateCommandHandler(init = true)
    OrderCreatedEvent handle(CreateOrderCommand cmd, OrderAggregateState state) {
        if (cmd.getOrderId() == null || cmd.getOrderId().isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        return new OrderCreatedEvent(cmd.getOrderId(), cmd.getDescription(), cmd.getQuantity());
    }

    @EventSourcingHandler
    OrderAggregateState on(OrderCreatedEvent e, OrderAggregateState state) {
        if (state == null) state = new OrderAggregateState();
        state.setDescription(e.getDescription());
        state.setQuantity(e.getQuantity());
        return state;
    }
}
