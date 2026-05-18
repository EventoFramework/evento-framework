package com.evento.transport;

import com.evento.transport.message.Message;
import com.evento.transport.state.ConnectionState;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Bidirectional, message-oriented transport SPI.
 *
 * <p>A {@code Transport} represents a single logical connection between a bundle and the
 * server. The implementation handles framing, codec, heartbeat policy, and the
 * underlying I/O (Netty, in-memory, etc.). It exposes a uniform send/receive/state API
 * to the layers above.
 *
 * <p>Instances are NOT meant to be reused after {@link #close()}. Reconnect is handled
 * by a higher-level {@code ManagedConnection} that wraps Transport + ReconnectStrategy.
 *
 * <p>Thread-safety: {@code send}, {@code state}, and listener registration must be
 * thread-safe. {@code connect}/{@code close} are expected to be called from a single
 * lifecycle thread.
 */
public interface Transport extends AutoCloseable {

    /**
     * Identifier of the remote peer, for logging and routing keys.
     * For a server-side transport, this is the bundle/instance id; for a client-side
     * transport, this is the server address.
     */
    String remoteId();

    /**
     * Current connection state. Lock-free read.
     */
    ConnectionState state();

    /**
     * Initiate the connection. The returned future completes when the handshake succeeds
     * (state transitions to {@link ConnectionState#CONNECTED}) or fails.
     *
     * <p>If the transport is already connecting or connected, the future returned is
     * the one for the in-flight attempt.
     */
    CompletableFuture<Void> connect();

    /**
     * Send a message. The returned future completes once the message has been
     * accepted by the underlying I/O channel (not when the peer has processed it).
     *
     * <p>Throws {@link SendFailedException} synchronously if the transport is not in
     * a sendable state. Network-level failures are propagated by completing the
     * future exceptionally.
     */
    CompletableFuture<Void> send(Message message);

    /**
     * Register a handler for inbound messages. May be called only once during setup.
     */
    void onMessage(Consumer<Message> handler);

    /**
     * Subscribe to state transitions. Listeners are invoked synchronously on the thread
     * that performed the transition; do not block.
     */
    void onStateChange(BiConsumer<ConnectionState, ConnectionState> listener);

    /**
     * Best-effort timestamp (epoch millis) of the last message received from the peer.
     * Used by failure detectors. Returns 0 if no message has arrived yet.
     */
    long lastInboundMs();

    /**
     * Close the connection. Idempotent. Pending sends complete exceptionally.
     */
    @Override
    void close();
}
