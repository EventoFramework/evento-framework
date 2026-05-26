package com.evento.lab.bundle.consumer;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.api.event.OrderCancelledEvent;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.api.event.OrderUpdatedEvent;

/** Parallel no-op projector 4/5 — exercises multi-projector fan-out for the same event stream. */
@Projector(version = 1)
public class LabFake4Projector {

    @EventHandler(retry = 3)
    void on(OrderCreatedEvent event) {
        System.out.println(getClass().getSimpleName() + " HIT created=" + event.getOrderId());
    }

    @EventHandler
    void on(OrderUpdatedEvent event) {
        System.out.println(getClass().getSimpleName() + " HIT updated=" + event.getOrderId());
    }

    @EventHandler
    void on(OrderCancelledEvent event) {
        System.out.println(getClass().getSimpleName() + " HIT cancelled=" + event.getOrderId());
    }
}
