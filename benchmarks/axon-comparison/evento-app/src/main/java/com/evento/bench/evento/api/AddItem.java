package com.evento.bench.evento.api;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class AddItem extends DomainCommand {
    private String orderId;
    private String sku;
    private int quantity;

    public AddItem() {
    }

    public AddItem(String orderId, String sku, int quantity) {
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
    }

    @Override
    public String getAggregateId() {
        return orderId;
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
