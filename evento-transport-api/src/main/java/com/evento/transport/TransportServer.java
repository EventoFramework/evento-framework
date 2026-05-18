package com.evento.transport;

import java.util.function.Consumer;

/**
 * Server endpoint that accepts incoming {@link Transport} connections. One server
 * binds to a single port; each accepted client becomes a child {@link Transport}
 * delivered to the {@code onConnection} listener.
 *
 * <p>Lifecycle is explicit: {@code start(port)} binds and begins accepting,
 * {@code stop()} unbinds and closes all child transports.
 */
public interface TransportServer extends AutoCloseable {

    /**
     * Bind and begin accepting connections. Must not be called twice.
     *
     * @return the actual bound port (useful when port=0 for ephemeral assignment).
     */
    int start(int port);

    /**
     * Register the callback invoked for each accepted child transport. The callback
     * receives the child in its initial (post-TCP, pre-application-handshake) state.
     * Must be called before {@link #start(int)}.
     */
    void onConnection(Consumer<Transport> handler);

    /**
     * Unbind and close all child transports. Idempotent.
     */
    void stop();

    @Override
    default void close() {
        stop();
    }

    /**
     * The bound port after a successful {@link #start(int)}, or {@code -1} if not bound.
     */
    int boundPort();
}
