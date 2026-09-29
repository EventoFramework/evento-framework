package com.evento.lab.cluster.api.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderCreatedEvent extends DomainEvent {

    private String orderId;
    private String region;
    private String idempotencyKey;

    public OrderCreatedEvent() {
    }

    public OrderCreatedEvent(String orderId, String region, String idempotencyKey) {
        this.orderId = orderId;
        this.region = region;
        this.idempotencyKey = idempotencyKey;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
