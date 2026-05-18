package com.evento.transport;

import com.evento.transport.state.ConnectionState;

public class SendFailedException extends TransportException {

    private final ConnectionState state;

    public SendFailedException(String message, ConnectionState state) {
        super(message);
        this.state = state;
    }

    public SendFailedException(String message, ConnectionState state, Throwable cause) {
        super(message, cause);
        this.state = state;
    }

    public ConnectionState connectionState() {
        return state;
    }
}
