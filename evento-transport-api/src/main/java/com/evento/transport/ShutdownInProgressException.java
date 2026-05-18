package com.evento.transport;

public class ShutdownInProgressException extends TransportException {

    public ShutdownInProgressException(String message) {
        super(message);
    }
}
