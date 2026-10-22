package com.evento.test.shop;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderPaid extends DomainEvent {
    String orderId;
    public OrderPaid() {}
    public OrderPaid(String orderId) { this.orderId = orderId; }
}
