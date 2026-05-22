package com.evento.lab.ms.api.event;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

public class PaymentIntentOpenedEvent extends ServiceEvent {

    private String orderId;
    private String paymentIntentId;

    public PaymentIntentOpenedEvent() {
    }

    public PaymentIntentOpenedEvent(String orderId, String paymentIntentId) {
        this.orderId = orderId;
        this.paymentIntentId = paymentIntentId;
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
}
