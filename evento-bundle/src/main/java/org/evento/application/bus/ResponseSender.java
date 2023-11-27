package org.evento.application.bus;

import org.evento.common.messaging.bus.SendFailedException;

import java.io.Serializable;

/**
 * The ResponseSender interface provides a contract for sending response messages.
 *
 * <p>
 * Implementations of this interface should be responsible for sending a Serializable message as a response.
 * If the send operation fails, a SendFailedException will be thrown.
 * </p>
 */
public interface ResponseSender {

    /**
     * Sends a serializable message.
     *
     * @param message the message to be sent
     * @throws SendFailedException if the send operation fails
     */
    void send(Serializable message) throws SendFailedException;
}
