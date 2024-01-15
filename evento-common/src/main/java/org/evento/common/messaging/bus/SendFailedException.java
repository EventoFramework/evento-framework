package org.evento.common.messaging.bus;

/**
 * SendFailedException is an exception class that is thrown when a message sending operation fails.
 * It extends the Exception class.
 */
public class SendFailedException extends Exception{

    public SendFailedException(Throwable cause) {
        super(cause);
    }
}
