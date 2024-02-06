package com.evento.application.bus;

import com.evento.common.messaging.bus.SendFailedException;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;

/**
 * The EventoResponseSender interface defines a contract for sending EventoResponses.
 * Implementations of this interface must be able to send an EventoResponse.
 * <p>
 * Usage:
 * Implement this interface to send EventoResponses based on your desired implementation.
 * <p>
 * Example:
 * MyResponseSender implements EventoResponseSender {
 *     void send(EventoResponse response) throws SendFailedException {
 *         // Implementation details for sending the EventoResponse
 *     }
 * }
 */
public interface EventoResponseSender {

    /**
     * Sends the response of an event.
     *
     * @param response the response object containing the event information
     * @throws SendFailedException if sending the response fails
     */
    void send(EventoResponse response) throws SendFailedException;
}
