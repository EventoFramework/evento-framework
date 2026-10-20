package com.evento.test.broker.demo;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderCreated extends DomainEvent {
    String orderId;
    public OrderCreated() {}
    public OrderCreated(String orderId) { this.orderId = orderId; }
    public String orderId() { return orderId; }
}
