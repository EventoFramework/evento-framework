package com.evento.lab.bundle.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.lab.api.command.CreateOrderCommand;
import com.evento.lab.api.event.OrderCreatedEvent;

@Aggregate(snapshotFrequency = 5)
public class LabAggregate {

    @AggregateCommandHandler(init = true)
    OrderCreatedEvent handle(CreateOrderCommand cmd, LabAggregateState state) {
        if (cmd.getOrderId() == null || cmd.getOrderId().isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        return new OrderCreatedEvent(cmd.getOrderId(), cmd.getDescription(), cmd.getQuantity());
    }

    @EventSourcingHandler
    LabAggregateState on(OrderCreatedEvent e, LabAggregateState state) {
        if (state == null) state = new LabAggregateState();
        state.setDescription(e.getDescription());
        state.setQuantity(e.getQuantity());
        return state;
    }
}
