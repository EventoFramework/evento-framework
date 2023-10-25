package org.evento.common.messaging.bus;

public class SendFailedException extends Exception{

    public SendFailedException(Throwable cause) {
        super(cause);
    }
}
