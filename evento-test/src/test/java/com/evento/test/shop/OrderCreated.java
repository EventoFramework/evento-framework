package com.evento.test.shop;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderCreated extends DomainEvent {
    String orderId;
    int budget;
    public OrderCreated() {}
    public OrderCreated(String orderId, int budget) { this.orderId = orderId; this.budget = budget; }
    public String orderId() { return orderId; }
}
