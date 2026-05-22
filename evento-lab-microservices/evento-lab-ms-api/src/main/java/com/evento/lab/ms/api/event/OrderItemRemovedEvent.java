package com.evento.lab.ms.api.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderItemRemovedEvent extends DomainEvent {

    private String orderId;
    private String itemId;

    public OrderItemRemovedEvent() {
    }

    public OrderItemRemovedEvent(String orderId, String itemId) {
        this.orderId = orderId;
        this.itemId = itemId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }
}
