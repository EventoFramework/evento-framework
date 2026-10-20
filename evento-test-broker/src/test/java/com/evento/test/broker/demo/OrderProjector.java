package com.evento.test.broker.demo;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;

@Projector(version = 1)
public class OrderProjector {
    @EventHandler
    void on(OrderCreated e) {
        Orders.SEEN.add(e.orderId());
    }

    @EventHandler
    void on(NoteAdded e) {
        Orders.SEEN.add(e.orderId() + ":" + e.note());
    }

    @EventHandler
    void on(CustomerRegistered e) {
        Orders.SEEN.add(e.customerId() + ":" + e.name());
    }
}
