package com.evento.lab.bundle.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.lab.api.command.CreateOrderCommand;
import com.evento.lab.api.command.UpdateOrderCommand;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.api.event.OrderUpdatedEvent;

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

    @AggregateCommandHandler
    OrderUpdatedEvent handle(UpdateOrderCommand cmd, LabAggregateState state) {
        System.out.println("LabAggregate update count: " + state.getUpdateCount());
        return new OrderUpdatedEvent(cmd.getOrderId(), cmd.getDescription(), cmd.getQuantity());
    }

    @EventSourcingHandler
    void on(OrderUpdatedEvent e, LabAggregateState state) {
        state.setDescription(e.getDescription());
        state.setQuantity(e.getQuantity());
        state.setUpdateCount(state.getUpdateCount() + 1);
    }
}
