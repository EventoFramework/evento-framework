package com.evento.bench.evento.api;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class ItemAdded extends DomainEvent {
    private String orderId;
    private String sku;
    private int quantity;

    public ItemAdded() {
    }

    public ItemAdded(String orderId, String sku, int quantity) {
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
