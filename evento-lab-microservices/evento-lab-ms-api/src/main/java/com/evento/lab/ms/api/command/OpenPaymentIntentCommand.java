package com.evento.lab.ms.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class OpenPaymentIntentCommand extends ServiceCommand {

    private String orderId;
    private String paymentIntentId;

    public OpenPaymentIntentCommand() {
    }

    public OpenPaymentIntentCommand(String orderId, String paymentIntentId) {
        this.orderId = orderId;
        this.paymentIntentId = paymentIntentId;
    }

    @Override
    public String getLockId() {
        return "payment-intent-" + orderId;
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
