package com.evento.lab.bundle.consumer;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.bundle.LabStore;
import com.evento.lab.event.OrderCancelledEvent;
import com.evento.lab.event.OrderConfirmedEvent;
import com.evento.lab.event.OrderCreatedEvent;
import com.evento.lab.view.OrderView;

@Projector(version = 1)
public class LabProjector {

    @EventHandler
    void on(OrderCreatedEvent e) {
        LabStore.put(new OrderView(e.getOrderId(), e.getDescription(), e.getQuantity(), "CREATED", false));
    }

    @EventHandler
    void on(OrderConfirmedEvent e) {
        var v = LabStore.get(e.getOrderId());
        if (v != null) v.setStatus("CONFIRMED");
    }

    @EventHandler
    void on(OrderCancelledEvent e) {
        var v = LabStore.get(e.getOrderId());
        if (v != null) {
            v.setStatus("CANCELLED");
            v.setCancelled(true);
        }
    }
}
