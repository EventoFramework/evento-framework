package com.evento.test.shop;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderDeleted extends DomainEvent {
    String orderId;
    public OrderDeleted() {}
    public OrderDeleted(String orderId) { this.orderId = orderId; }
}
