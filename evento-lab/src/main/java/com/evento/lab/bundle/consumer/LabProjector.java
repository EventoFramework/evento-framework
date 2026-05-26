package com.evento.lab.bundle.consumer;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.bundle.LabStore;
import com.evento.lab.api.event.OrderCancelledEvent;
import com.evento.lab.api.event.OrderConfirmedEvent;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.api.event.OrderUpdatedEvent;
import com.evento.lab.api.view.OrderRichView;
import com.evento.lab.api.view.OrderView;

@Projector(version = 1)
public class LabProjector {

    @EventHandler
    void on(OrderCreatedEvent e) {
        long now = System.currentTimeMillis();
        LabStore.put(new OrderView(e.getOrderId(), e.getDescription(), e.getQuantity(), "CREATED", false));
        LabStore.putRich(new OrderRichView(e.getOrderId(), e.getDescription(), e.getQuantity(),
                "CREATED", false, now, now));
    }

    @EventHandler(retry = 3)
    void on(OrderUpdatedEvent e) {
        long now = System.currentTimeMillis();
        var v = LabStore.get(e.getOrderId());
        if (v != null) {
            v.setDescription(e.getDescription());
            v.setQuantity(e.getQuantity());
        }
        var rich = LabStore.getRich(e.getOrderId());
        if (rich != null) {
            rich.setDescription(e.getDescription());
            rich.setQuantity(e.getQuantity());
            rich.setUpdatedAt(now);
        }
    }

    @EventHandler
    void on(OrderConfirmedEvent e) {
        var v = LabStore.get(e.getOrderId());
        if (v != null) v.setStatus("CONFIRMED");
        var rich = LabStore.getRich(e.getOrderId());
        if (rich != null) { rich.setStatus("CONFIRMED"); rich.setUpdatedAt(System.currentTimeMillis()); }
    }

    @EventHandler
    void on(OrderCancelledEvent e) {
        var v = LabStore.get(e.getOrderId());
        if (v != null) { v.setStatus("CANCELLED"); v.setCancelled(true); }
        var rich = LabStore.getRich(e.getOrderId());
        if (rich != null) {
            rich.setStatus("CANCELLED");
            rich.setCancelled(true);
            rich.setUpdatedAt(System.currentTimeMillis());
        }
    }
}
