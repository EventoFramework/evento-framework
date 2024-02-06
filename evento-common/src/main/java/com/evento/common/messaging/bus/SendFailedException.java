package com.evento.common.messaging.bus;

/**
 * SendFailedException is an exception class that is thrown when a message sending operation fails.
 * It extends the Exception class.
 */
public class SendFailedException extends Exception{

    /**
     * Constructs a new SendFailedException with the specified cause.
     *
     * @param cause the cause (which is saved for later retrieval by the Throwable.getCause() method)
     */
    public SendFailedException(Throwable cause) {
        super(cause);
    }
}
