package com.evento.lab.bundle.consumer;

import com.evento.common.modeling.annotations.component.Observer;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.bundle.LabStore;
import com.evento.lab.api.event.OrderConfirmedEvent;
import com.evento.lab.api.event.OrderCreatedEvent;

@Observer(version = 1)
public class LabObserver {

    @EventHandler
    void on(OrderCreatedEvent e) {
        LabStore.recordEvent("created:" + e.getOrderId());
    }

    @EventHandler
    void on(OrderConfirmedEvent e) {
        LabStore.recordEvent("confirmed:" + e.getOrderId());
    }
}
