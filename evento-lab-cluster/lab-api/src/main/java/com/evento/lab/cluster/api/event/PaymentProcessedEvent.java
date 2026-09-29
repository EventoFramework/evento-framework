package com.evento.lab.cluster.api.event;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

public class PaymentProcessedEvent extends ServiceEvent {

    private String orderId;
    private String paymentId;
    private int attempt;

    public PaymentProcessedEvent() {
    }

    public PaymentProcessedEvent(String orderId, String paymentId, int attempt) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.attempt = attempt;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }
}
