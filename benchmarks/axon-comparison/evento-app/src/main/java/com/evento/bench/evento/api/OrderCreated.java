package com.evento.bench.evento.api;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderCreated extends DomainEvent {
    private String orderId;

    public OrderCreated() {
    }

    public OrderCreated(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
