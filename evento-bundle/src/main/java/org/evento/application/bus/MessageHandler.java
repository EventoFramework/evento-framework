package org.evento.application.bus;

import java.io.Serializable;

/**
 * Interface for handling messages and providing a response sender.
 * <p>
 * The MessageHandler interface defines a method for handling messages along with a response sender.
 * The handle method takes a Serializable message and a ResponseSender as arguments and does not return any value.
 * Implementations of this interface should process the message and send a response using the provided ResponseSender.
 */
public interface MessageHandler {
    /**
     * Handles a serializable message and sends a response through the provided response sender.
     *
     * @param message The serializable message to be handled.
     * @param sender  The response sender to send the response through.
     */
    void handle(Serializable message, ResponseSender sender);
}
