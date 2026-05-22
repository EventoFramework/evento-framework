package com.evento.lab.ms.api.event;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

public class OrderConfirmedEvent extends ServiceEvent {

    private String orderId;

    public OrderConfirmedEvent() {
    }

    public OrderConfirmedEvent(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
