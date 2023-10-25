package org.evento.application.bus;

import org.evento.common.messaging.bus.SendFailedException;
import org.evento.common.modeling.messaging.message.internal.EventoResponse;

public interface EventoResponseSender {

    public void send(EventoResponse response) throws SendFailedException;
}
