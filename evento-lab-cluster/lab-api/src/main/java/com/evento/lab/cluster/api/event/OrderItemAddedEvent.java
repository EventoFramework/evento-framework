package com.evento.lab.cluster.api.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderItemAddedEvent extends DomainEvent {

    private String orderId;
    private String sku;
    private int qty;
    private String idempotencyKey;

    public OrderItemAddedEvent() {
    }

    public OrderItemAddedEvent(String orderId, String sku, int qty, String idempotencyKey) {
        this.orderId = orderId;
        this.sku = sku;
        this.qty = qty;
        this.idempotencyKey = idempotencyKey;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
