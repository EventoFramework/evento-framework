package org.evento.application.bus;

public interface MessageHandler {
    public void handle(String string, ResponseSender sender);
}
