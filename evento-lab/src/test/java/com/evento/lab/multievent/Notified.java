package com.evento.lab.multievent;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

public class Notified extends ServiceEvent {
    private String notificationId;

    public Notified() {}

    public Notified(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getNotificationId() { return notificationId; }
}
