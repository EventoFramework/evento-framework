package com.evento.lab.ms.observer.observer;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.component.Observer;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.lab.ms.api.command.SendNotificationCommand;
import com.evento.lab.ms.api.event.OrderCancelledEvent;
import com.evento.lab.ms.api.event.OrderCompletedEvent;
import com.evento.lab.ms.api.event.OrderConfirmedEvent;
import com.evento.lab.ms.api.event.OrderCreatedEvent;

@Observer(version = 1)
public class OrderObserver {

    @EventHandler
    void on(OrderCreatedEvent e, CommandGateway cg) throws Exception {
        MsObservedEvents.record("created:" + e.getOrderId());
        cg.send(new SendNotificationCommand(e.getOrderId(), "Order created: " + e.getOrderId(), "EMAIL")).get();
    }

    @EventHandler
    void on(OrderConfirmedEvent e, CommandGateway cg) throws Exception {
        MsObservedEvents.record("confirmed:" + e.getOrderId());
        cg.send(new SendNotificationCommand(e.getOrderId(), "Order confirmed: " + e.getOrderId(), "EMAIL")).get();
    }

    @EventHandler
    void on(OrderCancelledEvent e, CommandGateway cg) throws Exception {
        MsObservedEvents.record("cancelled:" + e.getOrderId());
        cg.send(new SendNotificationCommand(e.getOrderId(), "Order cancelled: " + e.getOrderId(), "SMS")).get();
    }

    @EventHandler
    void on(OrderCompletedEvent e, CommandGateway cg) throws Exception {
        MsObservedEvents.record("completed:" + e.getOrderId());
        cg.send(new SendNotificationCommand(e.getOrderId(), "Order completed: " + e.getOrderId(), "EMAIL")).get();
    }
}
