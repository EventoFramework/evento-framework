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
     * Zero-copy send: write a pre-encoded frame body directly, bypassing the
     * codec. Used by the broker to forward an inbound {@link Frame#rawBytes}
     * to another peer without re-running the codec.
     *
     * <p>{@code frameBytes} is the CBOR body of one wire frame, exactly as
     * produced by the codec — the transport's framing layer (length prefix,
     * TLS, etc.) is applied as usual.
     *
     * <p>Default implementation falls back to {@link #send(Message)} after
     * decoding the bytes, so transports that don't have a fast path (e.g.
     * in-memory test doubles) remain correct. Production Netty transports
     * override this with a direct ByteBuf write.
     */
    default CompletableFuture<Void> sendRaw(byte[] frameBytes) {
        throw new UnsupportedOperationException(
                "sendRaw is not implemented by this transport; "
                        + "decode the bytes and call send(Message) instead");
    }

    /**
     * Register a handler for inbound messages. May be called only once during setup.
     */
    void onMessage(Consumer<Message> handler);

    /**
     * Frame-aware variant of {@link #onMessage}. The handler receives both the
     * parsed {@link Message} and the {@link Frame#rawBytes} it was decoded
     * from, so callers that only forward (the broker on its hot path) can
     * route by message metadata then write the raw bytes via
     * {@link #sendRaw} without re-encoding.
     *
     * <p>Default implementation wraps the parsed message into a {@link Frame}
     * with an empty raw-byte slot — fine for in-memory test doubles where
     * there is no on-wire representation. Production Netty transports
     * override this to keep the actual byte buffer.
     *
     * <p>Calling both {@link #onMessage} and {@link #onFrame} is unsupported;
     * pick one.
     */
    default void onFrame(Consumer<Frame> handler) {
        onMessage(msg -> handler.accept(new Frame(msg, new byte[0])));
    }

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
