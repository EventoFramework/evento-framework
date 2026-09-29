package com.evento.application.client.connection;

/**
 * The broker answered the handshake but cannot take this bundle: it announced
 * another server instance or cluster than the bundle requires, speaks another
 * protocol version, or rejected the {@code Hello}. Unlike a refused connection
 * or a timeout, this does not go away by retrying a few seconds later — it
 * lasts until someone changes the broker or the bundle's configuration — so the
 * fail-back logic counts it separately and stops retrying after a few (N16).
 *
 * <p>An {@link IllegalStateException}, as these failures were before the type
 * existed, so code that caught them keeps working.
 */
public class BrokerRefusedException extends IllegalStateException {

    public BrokerRefusedException(String message) {
        super(message);
    }
}
