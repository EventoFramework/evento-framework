package com.evento.lab.ms.observer.service;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.lab.ms.api.command.SendNotificationCommand;
import com.evento.lab.ms.api.event.NotificationSentEvent;
import com.evento.lab.ms.observer.store.MsNotificationLog;

@Service
public class NotificationService {

    @CommandHandler
    NotificationSentEvent handle(SendNotificationCommand cmd) {
        MsNotificationLog.record(cmd.getChannel() + ":" + cmd.getOrderId() + ":" + cmd.getMessage());
        return new NotificationSentEvent(cmd.getOrderId(), cmd.getMessage(), cmd.getChannel());
    }
}
