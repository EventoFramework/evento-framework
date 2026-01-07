package com.evento.common.messaging.bus;

public class EventoRequestCorrelationExpiredException extends Exception {

    public EventoRequestCorrelationExpiredException() {
        super();
    }

    public EventoRequestCorrelationExpiredException(String message) {
        super(message);
    }

    public EventoRequestCorrelationExpiredException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventoRequestCorrelationExpiredException(Throwable cause) {
        super(cause);
    }

    protected EventoRequestCorrelationExpiredException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
