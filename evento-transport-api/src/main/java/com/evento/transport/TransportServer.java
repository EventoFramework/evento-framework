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

    /**
     * Gate for health probes: a transport-level Ping that arrives on a connection
     * before any other frame (no session behind it) is answered only while
     * {@code ready} returns true, so that a peer probing this server does not
     * take a bound port for a server ready to serve. Sessions that have sent
     * anything else are not affected. The default ignores the gate (every Ping
     * is answered), which is what a transport without probes needs.
     *
     * @param ready readiness of the server; null answers every Ping
     */
    default void setPreSessionReadiness(java.util.function.BooleanSupplier ready) {
    }
}
