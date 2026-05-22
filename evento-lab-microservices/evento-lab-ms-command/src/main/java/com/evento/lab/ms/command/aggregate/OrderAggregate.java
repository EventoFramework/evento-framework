package com.evento.lab.ms.command.aggregate;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.lab.ms.api.command.AddOrderItemCommand;
import com.evento.lab.ms.api.command.CreateOrderCommand;
import com.evento.lab.ms.api.command.RemoveOrderItemCommand;
import com.evento.lab.ms.api.event.OrderCreatedEvent;
import com.evento.lab.ms.api.event.OrderItemAddedEvent;
import com.evento.lab.ms.api.event.OrderItemRemovedEvent;

@Aggregate(snapshotFrequency = 5)
public class OrderAggregate {

    @AggregateCommandHandler(init = true)
    OrderCreatedEvent handle(CreateOrderCommand cmd, OrderAggregateState state) {
        if (cmd.getOrderId() == null || cmd.getOrderId().isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        return new OrderCreatedEvent(cmd.getOrderId(), cmd.getDescription(), cmd.getQuantity(), cmd.getContext());
    }

    @AggregateCommandHandler
    OrderItemAddedEvent handle(AddOrderItemCommand cmd, OrderAggregateState state) {
        return new OrderItemAddedEvent(cmd.getOrderId(), cmd.getItemId(), cmd.getName(), cmd.getPrice(), cmd.getQuantity());
    }

    @AggregateCommandHandler
    OrderItemRemovedEvent handle(RemoveOrderItemCommand cmd, OrderAggregateState state) {
        return new OrderItemRemovedEvent(cmd.getOrderId(), cmd.getItemId());
    }

    @EventSourcingHandler
    OrderAggregateState on(OrderCreatedEvent e, OrderAggregateState state) {
        if (state == null) state = new OrderAggregateState();
        state.setDescription(e.getDescription());
        state.setQuantity(e.getQuantity());
        state.setContext(e.getContext());
        state.setStatus("CREATED");
        return state;
    }

    @EventSourcingHandler
    OrderAggregateState on(OrderItemAddedEvent e, OrderAggregateState state) {
        if (state == null) state = new OrderAggregateState();
        state.getItems().add(new OrderItem(e.getItemId(), e.getName(), e.getPrice(), e.getQuantity()));
        return state;
    }

    @EventSourcingHandler
    OrderAggregateState on(OrderItemRemovedEvent e, OrderAggregateState state) {
        if (state == null) state = new OrderAggregateState();
        state.getItems().removeIf(item -> item.getItemId().equals(e.getItemId()));
        return state;
    }
}
