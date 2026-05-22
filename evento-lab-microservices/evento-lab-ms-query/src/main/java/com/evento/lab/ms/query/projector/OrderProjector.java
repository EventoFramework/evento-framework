package com.evento.lab.ms.query.projector;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.ms.api.event.OrderCancelledEvent;
import com.evento.lab.ms.api.event.OrderCompletedEvent;
import com.evento.lab.ms.api.event.OrderConfirmedEvent;
import com.evento.lab.ms.api.event.OrderCreatedEvent;
import com.evento.lab.ms.api.event.OrderItemAddedEvent;
import com.evento.lab.ms.api.event.OrderItemRemovedEvent;
import com.evento.lab.ms.api.view.OrderItemView;
import com.evento.lab.ms.api.view.OrderView;
import com.evento.lab.ms.query.store.OrderViewStore;

@Projector(version = 1)
public class OrderProjector {

    @EventHandler
    void on(OrderCreatedEvent e) {
        var view = new OrderView(e.getOrderId(), e.getDescription(), e.getQuantity(), "CREATED", false);
        view.setContext(e.getContext());
        OrderViewStore.put(view);
    }

    @EventHandler
    void on(OrderConfirmedEvent e) {
        var v = OrderViewStore.get(e.getOrderId());
        if (v != null) v.setStatus("CONFIRMED");
    }

    @EventHandler
    void on(OrderCancelledEvent e) {
        var v = OrderViewStore.get(e.getOrderId());
        if (v != null) {
            v.setStatus("CANCELLED");
            v.setCancelled(true);
        }
    }

    @EventHandler
    void on(OrderItemAddedEvent e) {
        var v = OrderViewStore.get(e.getOrderId());
        if (v == null) {
            v = new OrderView();
            v.setOrderId(e.getOrderId());
            OrderViewStore.put(v);
        }
        v.getItems().add(new OrderItemView(e.getItemId(), e.getName(), e.getPrice(), e.getQuantity()));
    }

    @EventHandler
    void on(OrderItemRemovedEvent e) {
        var v = OrderViewStore.get(e.getOrderId());
        if (v != null) {
            v.getItems().removeIf(item -> item.getItemId().equals(e.getItemId()));
        }
    }

    @EventHandler
    void on(OrderCompletedEvent e) {
        var v = OrderViewStore.get(e.getOrderId());
        if (v != null) v.setStatus("COMPLETED");
    }
}
