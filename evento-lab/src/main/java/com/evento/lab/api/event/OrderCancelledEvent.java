package com.evento.lab.api.event;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

public class OrderCancelledEvent extends ServiceEvent {

    private String orderId;
    private String reason;

    public OrderCancelledEvent() {
    }

    public OrderCancelledEvent(String orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
