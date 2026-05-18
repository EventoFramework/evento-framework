package com.evento.transport.inmemory;

import com.evento.transport.SendFailedException;
import com.evento.transport.Transport;
import com.evento.transport.message.Message;
import com.evento.transport.state.ConnectionState;
import com.evento.transport.state.ConnectionStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * In-process {@link Transport} implementation. Two instances can be paired via
 * {@link #pair(String, String)} so that {@code send()} on one delivers to the
 * {@code onMessage} handler of the other.
 *
 * <p>Intended for tests and SPI experimentation. Delivery happens on a configurable
 * executor (virtual-thread by default) to simulate the asynchronous nature of a
 * networked transport. Failures can be injected via {@link #failNextSends(int)}
 * and disconnections via {@link #simulateDisconnect()}.
 *
 * <p>This is not thread-safe to <em>build</em> (i.e. {@code onMessage} registration),
 * but is fully thread-safe to use after the handlers are wired.
 */
public final class InMemoryTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTransport.class);

    private final String remoteId;
    private final ConnectionStateMachine state;
    private final Executor deliveryExecutor;
    private final AtomicLong lastInboundMs = new AtomicLong(0L);
    private volatile InMemoryTransport peer;
    private volatile Consumer<Message> messageHandler;
    private volatile int failNextSends = 0;

    private InMemoryTransport(String remoteId, Executor executor) {
        this.remoteId = remoteId;
        this.state = new ConnectionStateMachine(remoteId);
        this.deliveryExecutor = executor;
    }

    /**
     * Build two transports that send to each other. Initial state is DISCONNECTED;
     * call {@link #connect()} on both to advance to CONNECTED via the local state
     * machine (no real handshake — that's exercised by NettyTransport in PR1).
     */
    public static Pair pair(String clientId, String serverId) {
        return pair(clientId, serverId, Executors.newVirtualThreadPerTaskExecutor());
    }

    public static Pair pair(String clientId, String serverId, Executor sharedExecutor) {
        var client = new InMemoryTransport(clientId, sharedExecutor);
        var server = new InMemoryTransport(serverId, sharedExecutor);
        client.peer = server;
        server.peer = client;
        return new Pair(client, server);
    }

    @Override
    public String remoteId() {
        return remoteId;
    }

    @Override
    public ConnectionState state() {
        return state.current();
    }

    @Override
    public CompletableFuture<Void> connect() {
        if (peer == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("transport " + remoteId + " has no peer"));
        }
        state.compareAndTransition(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING, "connect()");
        state.compareAndTransition(ConnectionState.CONNECTING, ConnectionState.HANDSHAKING, "skip-handshake");
        state.compareAndTransition(ConnectionState.HANDSHAKING, ConnectionState.CONNECTED, "in-memory-link");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> send(Message message) {
        Objects.requireNonNull(message, "message");
        var snapshot = state.current();
        if (!snapshot.canSend()) {
            throw new SendFailedException("not in CONNECTED state: " + snapshot, snapshot);
        }
        if (peer == null) {
            throw new SendFailedException("no peer", snapshot);
        }
        if (failNextSends > 0) {
            failNextSends--;
            var failure = new SendFailedException("injected failure", snapshot);
            return CompletableFuture.failedFuture(failure);
        }
        var future = new CompletableFuture<Void>();
        deliveryExecutor.execute(() -> {
            try {
                peer.deliver(message);
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private void deliver(Message message) {
        lastInboundMs.set(System.currentTimeMillis());
        var handler = messageHandler;
        if (handler == null) {
            log.warn("event=drop_no_handler remote={} type={}", remoteId, message.getClass().getSimpleName());
            return;
        }
        handler.accept(message);
    }

    @Override
    public void onMessage(Consumer<Message> handler) {
        if (this.messageHandler != null) {
            throw new IllegalStateException("onMessage handler already registered for " + remoteId);
        }
        this.messageHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public void onStateChange(BiConsumer<ConnectionState, ConnectionState> listener) {
        state.addListener(listener);
    }

    @Override
    public long lastInboundMs() {
        return lastInboundMs.get();
    }

    @Override
    public void close() {
        var previous = state.forceTransition(ConnectionState.CLOSED, "close()");
        if (previous != ConnectionState.CLOSED && peer != null) {
            peer.state.forceTransition(ConnectionState.CLOSED, "peer-closed");
        }
    }

    /** Inject {@code n} consecutive synthetic send failures (for tests). */
    public void failNextSends(int n) {
        if (n < 0) throw new IllegalArgumentException("n must be >= 0");
        this.failNextSends = n;
    }

    /** Simulate an abrupt disconnect: state → DEGRADED on this side, peer → DEGRADED. */
    public void simulateDisconnect() {
        state.compareAndTransition(ConnectionState.CONNECTED, ConnectionState.DEGRADED, "simulate_disconnect");
        if (peer != null) {
            peer.state.compareAndTransition(ConnectionState.CONNECTED, ConnectionState.DEGRADED, "peer_disconnect");
        }
    }

    public record Pair(InMemoryTransport client, InMemoryTransport server) {}
}
