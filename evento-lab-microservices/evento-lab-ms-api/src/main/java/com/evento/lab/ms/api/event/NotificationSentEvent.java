package com.evento.lab.ms.api.event;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

public class NotificationSentEvent extends ServiceEvent {

    private String orderId;
    private String message;
    private String channel;

    public NotificationSentEvent() {
    }

    public NotificationSentEvent(String orderId, String message, String channel) {
        this.orderId = orderId;
        this.message = message;
        this.channel = channel;
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
