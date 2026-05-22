package com.evento.lab.ms.api.event;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

public class PaymentStatusChangedEvent extends ServiceEvent {

    private String orderId;
    private String paymentIntentId;
    private String status;

    public PaymentStatusChangedEvent() {
    }

    public PaymentStatusChangedEvent(String orderId, String paymentIntentId, String status) {
        this.orderId = orderId;
        this.paymentIntentId = paymentIntentId;
        this.status = status;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
