package com.evento.lab.ms.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class CompleteOrderCommand extends ServiceCommand {

    private String orderId;

    public CompleteOrderCommand() {
    }

    public CompleteOrderCommand(String orderId) {
        this.orderId = orderId;
    }

    @Override
    public String getLockId() {
        return "complete-" + orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
