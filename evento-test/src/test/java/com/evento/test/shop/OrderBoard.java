package com.evento.test.shop;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.utils.ProjectorStatus;

import java.util.HashMap;
import java.util.Map;

/** Counts the items per order; notifies only on live events, not on a replay. */
@Projector(version = 1)
public class OrderBoard {

    private final Map<String, Integer> items;

    public OrderBoard(Map<String, Integer> items) {
        this.items = items;
    }

    public OrderBoard() {
        this(new HashMap<>());
    }

    @EventHandler
    void on(OrderCreated e) {
        if (items.containsKey(e.orderId)) throw new IllegalStateException("duplicate order " + e.orderId);
        items.put(e.orderId, 0);
    }

    @EventHandler
    void on(ItemAdded e, ProjectorStatus status, CommandGateway commands) {
        items.merge(e.orderId, 1, Integer::sum);
        if (status.isHeadReached()) commands.send(new Notify(e.orderId));
    }
}
