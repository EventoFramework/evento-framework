package com.evento.lab.multievent;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class Notify extends ServiceCommand {
    private String notificationId;
    private boolean silent;

    public Notify() {}

    public Notify(String notificationId, boolean silent) {
        this.notificationId = notificationId;
        this.silent = silent;
    }

    public String getNotificationId() { return notificationId; }
    public boolean isSilent() { return silent; }
}
