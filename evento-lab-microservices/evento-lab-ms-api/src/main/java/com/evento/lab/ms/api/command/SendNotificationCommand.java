package com.evento.lab.ms.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class SendNotificationCommand extends ServiceCommand {

    private String orderId;
    private String message;
    private String channel;

    public SendNotificationCommand() {
    }

    public SendNotificationCommand(String orderId, String message, String channel) {
        this.orderId = orderId;
        this.message = message;
        this.channel = channel;
    }

    @Override
    public String getLockId() {
        return "notify-" + orderId + "-" + channel;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }
}
