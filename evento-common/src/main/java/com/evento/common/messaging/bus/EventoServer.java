package com.evento.common.messaging.bus;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    <T extends Serializable> CompletableFuture<T> request(Serializable request, long timeout, TimeUnit unit) throws SendFailedException;

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

    /**
     * Every write this process's commands made through this server so far (context →
     * highest event sequence number): what {@code QueryOptions#readYourWrites()} requires.
     * {@link com.evento.common.modeling.messaging.query.ConsistencyToken#NONE} when unknown.
     */
    default com.evento.common.modeling.messaging.query.ConsistencyToken ownWrites() {
        return com.evento.common.modeling.messaging.query.ConsistencyToken.NONE;
    }

    /**
     * Asks {@code query} for a streamed answer: views pulled as they are consumed, with
     * flow control end to end. {@code null} when this server cannot stream, in which case
     * the gateway asks the query the plain way and iterates the whole answer.
     *
     * @param query         the query message (metadata and consistency set)
     * @param options       window and transport preferences
     * @param timeoutMillis how long to wait for the first views, and then between batches
     */
    default <V extends com.evento.common.modeling.messaging.payload.View>
    com.evento.common.messaging.gateway.QueryStream<V> streamQuery(
            com.evento.common.modeling.messaging.message.application.QueryMessage<?> query,
            com.evento.common.messaging.gateway.QueryOptions options, long timeoutMillis) {
        return null;
    }
}
