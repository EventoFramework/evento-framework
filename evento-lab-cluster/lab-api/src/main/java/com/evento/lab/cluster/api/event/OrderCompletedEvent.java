package com.evento.lab.cluster.api.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderCompletedEvent extends DomainEvent {

    private String orderId;
    private String idempotencyKey;

    public OrderCompletedEvent() {
    }

    public OrderCompletedEvent(String orderId, String idempotencyKey) {
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
