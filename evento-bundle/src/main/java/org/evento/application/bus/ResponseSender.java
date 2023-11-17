package org.evento.application.bus;

import org.evento.common.messaging.bus.SendFailedException;

import java.io.Serializable;

public interface ResponseSender {

    void send(Serializable message) throws SendFailedException;
}
