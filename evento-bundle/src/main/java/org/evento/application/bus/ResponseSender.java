package org.evento.application.bus;

import org.evento.common.messaging.bus.SendFailedException;

public interface ResponseSender {

    public void send(String string) throws SendFailedException;
}
