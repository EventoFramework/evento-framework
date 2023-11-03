package org.evento.application.bus;

import java.io.Serializable;

public interface MessageHandler {
    public void handle(Serializable message, ResponseSender sender);
}
