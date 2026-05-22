package com.evento.lab.ms.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class ConfirmOrderCommand extends ServiceCommand {

    private String orderId;

    public ConfirmOrderCommand() {
    }

    public ConfirmOrderCommand(String orderId) {
        this.orderId = orderId;
    }

    @Override
    public String getLockId() {
        return "confirm-" + orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
