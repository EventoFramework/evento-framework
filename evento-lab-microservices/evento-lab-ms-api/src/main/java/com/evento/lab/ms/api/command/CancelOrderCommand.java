package com.evento.lab.ms.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class CancelOrderCommand extends ServiceCommand {

    private String orderId;
    private String reason;

    public CancelOrderCommand() {
    }

    public CancelOrderCommand(String orderId) {
        this.orderId = orderId;
    }

    public CancelOrderCommand(String orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    @Override
    public String getLockId() {
        return "cancel-" + orderId;
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
