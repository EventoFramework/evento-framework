package com.evento.lab.ms.query.projector;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.ms.api.event.OrderCancelledEvent;
import com.evento.lab.ms.api.event.OrderConfirmedEvent;
import com.evento.lab.ms.api.event.OrderCreatedEvent;
import com.evento.lab.ms.api.view.OrderView;
import com.evento.lab.ms.query.store.OrderViewStore;

@Projector(version = 1)
public class OrderProjector {

    @EventHandler
    void on(OrderCreatedEvent e) {
        OrderViewStore.put(new OrderView(e.getOrderId(), e.getDescription(), e.getQuantity(), "CREATED", false));
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
}
