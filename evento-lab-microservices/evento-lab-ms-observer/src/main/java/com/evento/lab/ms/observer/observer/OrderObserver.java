package com.evento.lab.ms.observer.observer;

import com.evento.common.modeling.annotations.component.Observer;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.ms.api.event.OrderCancelledEvent;
import com.evento.lab.ms.api.event.OrderConfirmedEvent;
import com.evento.lab.ms.api.event.OrderCreatedEvent;

@Observer(version = 1)
public class OrderObserver {

    @EventHandler
    void on(OrderCreatedEvent e) {
        MsObservedEvents.record("created:" + e.getOrderId());
    }

    @EventHandler
    void on(OrderConfirmedEvent e) {
        MsObservedEvents.record("confirmed:" + e.getOrderId());
    }

    @EventHandler
    void on(OrderCancelledEvent e) {
        MsObservedEvents.record("cancelled:" + e.getOrderId());
    }
}
