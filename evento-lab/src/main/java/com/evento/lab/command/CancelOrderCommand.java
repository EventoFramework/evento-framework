package com.evento.lab.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class CancelOrderCommand extends ServiceCommand {

    private String orderId;

    public CancelOrderCommand() {
    }

    public CancelOrderCommand(String orderId) {
        this.orderId = orderId;
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
}
