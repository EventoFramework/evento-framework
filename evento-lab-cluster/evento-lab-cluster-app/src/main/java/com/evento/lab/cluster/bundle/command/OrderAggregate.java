package com.evento.lab.cluster.bundle.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.lab.cluster.api.command.AddOrderItemCommand;
import com.evento.lab.cluster.api.command.CompleteOrderCommand;
import com.evento.lab.cluster.api.command.CreateOrderCommand;
import com.evento.lab.cluster.api.event.OrderCompletedEvent;
import com.evento.lab.cluster.api.event.OrderCreatedEvent;
import com.evento.lab.cluster.api.event.OrderItemAddedEvent;

/**
 * The order aggregate, hosted by every cell's {@code cluster-command} bundle.
 *
 * <p>Every event carries the order's region as its Evento context: that is
 * what the per-region projectors filter on. The region comes from the command
 * on creation and from the state afterwards, so an item added through another
 * region's gateway (S5) still lands in the order's own region.
 *
 * <p>The domain rules (no item after completion, one completion) double as the
 * I3 "no fork" oracle: a story that violates them can only have been written
 * by two servers that each appended from a stale story.
 */
@Aggregate(snapshotFrequency = 20)
public class OrderAggregate {

    @AggregateCommandHandler(init = true)
    OrderCreatedEvent handle(CreateOrderCommand cmd, OrderAggregateState state) {
        if (cmd.getOrderId() == null || cmd.getOrderId().isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (cmd.getRegion() == null || cmd.getRegion().isBlank()) {
            throw new IllegalArgumentException("region is required");
        }
        return new OrderCreatedEvent(cmd.getOrderId(), cmd.getRegion(), cmd.getIdempotencyKey())
                .setContext(cmd.getRegion());
    }

    @AggregateCommandHandler
    OrderItemAddedEvent handle(AddOrderItemCommand cmd, OrderAggregateState state) {
        if ("COMPLETED".equals(state.getStatus())) {
            throw new IllegalStateException("order " + cmd.getOrderId() + " is already completed");
        }
        if (cmd.getQty() <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        return new OrderItemAddedEvent(cmd.getOrderId(), cmd.getSku(), cmd.getQty(), cmd.getIdempotencyKey())
                .setContext(regionOf(state, cmd.getRegion()));
    }

    @AggregateCommandHandler
    OrderCompletedEvent handle(CompleteOrderCommand cmd, OrderAggregateState state) {
        if ("COMPLETED".equals(state.getStatus())) {
            throw new IllegalStateException("order " + cmd.getOrderId() + " is already completed");
        }
        return new OrderCompletedEvent(cmd.getOrderId(), cmd.getIdempotencyKey())
                .setContext(regionOf(state, cmd.getRegion()));
    }

    @EventSourcingHandler
    OrderAggregateState on(OrderCreatedEvent e, OrderAggregateState state) {
        var s = state == null ? new OrderAggregateState() : state;
        s.setRegion(e.getRegion());
        s.setStatus("CREATED");
        return s;
    }

    @EventSourcingHandler
    OrderAggregateState on(OrderItemAddedEvent e, OrderAggregateState state) {
        var s = state == null ? new OrderAggregateState() : state;
        s.setItemCount(s.getItemCount() + 1);
        s.setItemQty(s.getItemQty() + e.getQty());
        return s;
    }

    @EventSourcingHandler
    OrderAggregateState on(OrderCompletedEvent e, OrderAggregateState state) {
        var s = state == null ? new OrderAggregateState() : state;
        s.setStatus("COMPLETED");
        return s;
    }

    private static String regionOf(OrderAggregateState state, String fallback) {
        if (state != null && state.getRegion() != null && !state.getRegion().isBlank()) {
            return state.getRegion();
        }
        if (fallback == null || fallback.isBlank()) {
            throw new IllegalStateException("order has no region");
        }
        return fallback;
    }
}
