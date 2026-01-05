package com.evento.common.messaging.bus;

public class RequestTargetNodeLeftTheClusterException extends Exception {

    public RequestTargetNodeLeftTheClusterException() {
        super();
    }

    public RequestTargetNodeLeftTheClusterException(String message) {
        super(message);
    }

    public RequestTargetNodeLeftTheClusterException(String message, Throwable cause) {
        super(message, cause);
    }

    public RequestTargetNodeLeftTheClusterException(Throwable cause) {
        super(cause);
    }

    protected RequestTargetNodeLeftTheClusterException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
