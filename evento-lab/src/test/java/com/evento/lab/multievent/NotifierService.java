package com.evento.lab.multievent;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;

/** May answer without an event: its edge to Notified is a probability. */
@Service
public class NotifierService {

    @CommandHandler
    Notified handle(Notify c) {
        return c.isSilent() ? null : new Notified(c.getNotificationId());
    }
}
