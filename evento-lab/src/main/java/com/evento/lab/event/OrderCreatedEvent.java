package com.evento.lab.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderCreatedEvent extends DomainEvent {

    private String orderId;
    private String description;
    private int quantity;

    public OrderCreatedEvent() {
    }

    public OrderCreatedEvent(String orderId, String description, int quantity) {
        this.orderId = orderId;
        this.description = description;
        this.quantity = quantity;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
