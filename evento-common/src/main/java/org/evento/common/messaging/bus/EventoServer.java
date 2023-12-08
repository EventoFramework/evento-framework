package org.evento.common.messaging.bus;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;

/**
 * This interface represents a server for sending and receiving messages in an Evento cluster.
 */
public interface EventoServer {

    /**
     * Sends a message using the EventoServer.
     *
     * @param message the message to be sent
     * @throws SendFailedException if the message sending fails
     */
    void send(Serializable message) throws  SendFailedException;

    /**
     * Sends a request using the EventoServer.
     *
     * @param <T>      the type of the response
     * @param request  the request to be sent
     * @return a CompletableFuture that will be completed with the response
     * @throws SendFailedException if the request sending fails
     */
    <T extends Serializable> CompletableFuture<T> request(Serializable request) throws SendFailedException;

    /**
     * Returns the instance ID of the EventoServer.
     *
     * @return the instance ID
     */
    String getInstanceId();

    /**
     * Returns the bundle ID of the EventoServer.
     *
     * @return the bundle ID
     */
    String getBundleId();
}
