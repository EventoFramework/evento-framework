package com.evento.application.client.connection;

import com.evento.application.client.BundleClientConfig;
import com.evento.application.client.BundleClientState;
import com.evento.application.client.handshake.HelloFactory;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.SendFailedException;
import com.evento.transport.Transport;
import com.evento.transport.codec.PayloadCodec;
import com.evento.transport.message.Hello;
import com.evento.transport.message.Message;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Reject;
import com.evento.transport.message.Welcome;
import com.evento.transport.netty.NettyClientTransport;
import com.evento.transport.reconnect.ReconnectStrategy;
import com.evento.transport.protocol.BundleDiscoveryInfo;
import com.evento.transport.protocol.BundleRegistrationInfo;
import com.evento.transport.protocol.ProtocolNotifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Owns the per-bundle network connection: opens a {@link NettyClientTransport},
 * runs the {@code Hello → Welcome → register → enable} handshake, hands the
 * {@link Transport} to the dispatch layer, and on disconnect runs a backoff
 * reconnect loop that re-does the handshake transparently.
 *
 * <p>State machine for the bundle as a whole lives here (separate from the
 * per-transport {@code ConnectionState}). Outbound callers consult
 * {@link #state()} or {@link #currentTransport()}; sends issued while the
 * supervisor is between transports fail fast with {@link SendFailedException}.
 *
 * <p>Reconnect is mandatory by design: the supervisor only stops trying when
 * {@link #stop(Duration)} is called. A user that wants give-up-after-N
 * semantics injects a bounded {@link ReconnectStrategy}.
 */
public final class ConnectionSupervisor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConnectionSupervisor.class);

    private final BundleClientConfig config;
    private final PayloadCodec payloadCodec;
    private final ReconnectStrategy reconnectStrategy;
    private final AtomicReference<BundleClientState> state = new AtomicReference<>(BundleClientState.INITIAL);
    /**
     * Latches to {@code true} the first time {@link #markEnabled()} is called
     * (i.e., when the application calls {@link
     * com.evento.application.client.BundleClient#enable()}). Once set, every
     * future reconnect session re-sends {@code evento:enable} automatically
     * inside {@link #performRegistration} — matching the "send enable after
     * projectors are caught up" semantics from the initial connect without
     * requiring another application-level {@code enable()} call.
     */
    private final java.util.concurrent.atomic.AtomicBoolean enabledOnce =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final AtomicReference<NettyClientTransport> currentTransport = new AtomicReference<>();
    private final CopyOnWriteArrayList<BiConsumer<BundleClientState, BundleClientState>> stateListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<Welcome>> pendingHellos = new ConcurrentHashMap<>();

    /** Where inbound non-handshake messages (Request, Response, Notification, Reject) are routed. */
    private volatile Consumer<Message> inboundSink = m -> {};

    private volatile Thread supervisorThread;

    public ConnectionSupervisor(BundleClientConfig config, PayloadCodec payloadCodec) {
        this.config = config;
        this.payloadCodec = payloadCodec;
        this.reconnectStrategy = config.transportConfig().reconnectStrategy();
    }

    // ---------- public surface ----------

    public BundleClientState state() { return state.get(); }
    public NettyClientTransport currentTransport() { return currentTransport.get(); }

    public void onStateChange(BiConsumer<BundleClientState, BundleClientState> listener) {
        stateListeners.add(listener);
    }

    /**
     * Where to deliver every inbound message that isn't part of the handshake
     * dance. The supervisor owns Welcome/Reject; the caller takes everything
     * else (Response, Request, Notification, Ping/Pong — though Ping/Pong are
     * already swallowed by the transport's heartbeat handler).
     */
    public void setInboundSink(Consumer<Message> sink) {
        this.inboundSink = sink == null ? m -> {} : sink;
    }

    /**
     * Send {@code message} on the current transport. Throws if the bundle is
     * not READY.
     */
    public CompletableFuture<Void> send(Message message) {
        var snapshot = state.get();
        if (!snapshot.canSend()) {
            throw new SendFailedException("bundle not ready: state=" + snapshot, null);
        }
        var t = currentTransport.get();
        if (t == null) {
            throw new SendFailedException("transport not bound: state=" + snapshot, null);
        }
        return t.send(message);
    }

    /**
     * Start the supervisor. Returns when the first handshake either succeeds
     * or fails. The returned future tracks the first attempt — the supervisor
     * then keeps running in the background.
     */
    public CompletableFuture<Void> start() {
        if (!transitionTo(BundleClientState.INITIAL, BundleClientState.CONNECTING, "start")) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("supervisor already started: state=" + state.get()));
        }
        var firstAttempt = new CompletableFuture<Void>();
        supervisorThread = Thread.ofVirtual()
                .name("evento-supervisor-" + config.instanceId())
                .start(() -> runLoop(firstAttempt));
        return firstAttempt;
    }

    /**
     * Initiate graceful shutdown. Blocks up to {@code deadline} for in-flight
     * sends to drain, then closes the transport.
     */
    public void stop(Duration deadline) {
        var prev = state.getAndSet(BundleClientState.CLOSING);
        if (prev == BundleClientState.CLOSED) return;
        notifyStateChange(prev, BundleClientState.CLOSING);

        // Wake the reconnect loop so it observes CLOSING and exits.
        var thread = supervisorThread;
        if (thread != null) thread.interrupt();

        var t = currentTransport.getAndSet(null);
        if (t != null) {
            try { t.close(); } catch (Throwable ignored) {}
        }

        var deadlineEnd = System.currentTimeMillis() + deadline.toMillis();
        while (thread != null && thread.isAlive() && System.currentTimeMillis() < deadlineEnd) {
            try { Thread.sleep(20L); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }

        transitionTo(BundleClientState.CLOSING, BundleClientState.CLOSED, "stopped");
    }

    /**
     * Record that {@code evento:enable} has been sent at least once. After this
     * point every reconnect session will automatically re-send the enable
     * notification inside {@link #performRegistration}.
     */
    public void markEnabled() {
        enabledOnce.set(true);
    }

    @Override
    public void close() {
        stop(config.shutdownDeadline());
    }

    // ---------- internals ----------

    private void runLoop(CompletableFuture<Void> firstAttempt) {
        int attempt = 0;
        while (!state.get().isTerminal() && state.get() != BundleClientState.CLOSING) {
            attempt++;
            try {
                runOneSession(firstAttempt);
                attempt = 0;  // a successful session resets the backoff counter
            } catch (Throwable t) {
                log.warn("event=supervisor_session_failed attempt={} cause={}", attempt, t.toString());
                if (!firstAttempt.isDone() && attempt == 1) {
                    firstAttempt.completeExceptionally(t);
                }
                if (reconnectStrategy.shouldGiveUp(attempt)) {
                    log.error("event=supervisor_gave_up attempt={}", attempt);
                    transitionToForce(BundleClientState.CLOSED, "give_up");
                    return;
                }
            }
            if (state.get() == BundleClientState.CLOSING) break;
            transitionToForce(BundleClientState.RECONNECTING, "post_session");
            var delay = reconnectStrategy.nextDelay(attempt);
            try { Thread.sleep(delay.toMillis()); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        transitionToForce(BundleClientState.CLOSED, "loop_exit");
    }

    /**
     * Open a fresh transport, perform handshake + register, complete the
     * {@code firstAttempt} future as soon as the session reaches READY (so
     * callers don't have to wait for the transport to later drop), then block
     * here until the transport drops — at which point the run loop decides
     * whether to reconnect.
     */
    private void runOneSession(CompletableFuture<Void> firstAttempt) throws Exception {
        transitionToForce(BundleClientState.CONNECTING, "open_transport");
        var transport = new NettyClientTransport(
                config.bundleId() + "-" + config.instanceId(),
                config.host(), config.port(),
                config.transportConfig());
        var sessionEnded = new CompletableFuture<Void>();

        transport.onMessage(this::onMessage);
        transport.onStateChange((from, to) -> {
            if (to == com.evento.transport.state.ConnectionState.DISCONNECTED
                    || to == com.evento.transport.state.ConnectionState.CLOSED) {
                sessionEnded.complete(null);
            }
        });

        currentTransport.set(transport);
        try {
            transport.connect().get(config.handshakeTimeout().toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);

            transitionToForce(BundleClientState.HANDSHAKING, "tcp_up");
            performHandshake(transport);

            transitionToForce(BundleClientState.REGISTERING, "after_welcome");
            performRegistration(transport);

            transitionToForce(BundleClientState.READY, "registered");

            if (!firstAttempt.isDone()) firstAttempt.complete(null);
            sessionEnded.get();  // block until the transport drops
        } finally {
            currentTransport.compareAndSet(transport, null);
            try { transport.close(); } catch (Throwable ignored) {}
        }
    }

    private void performHandshake(NettyClientTransport transport) throws Exception {
        var hello = HelloFactory.build(config);
        var helloFuture = new CompletableFuture<Welcome>();
        pendingHellos.put(hello.correlationId(), helloFuture);
        try {
            transport.send(hello).get(config.handshakeTimeout().toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            var welcome = helloFuture.get(config.handshakeTimeout().toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            if (welcome.protocolVersion() != HandshakeProtocol.PROTOCOL_VERSION) {
                throw new IllegalStateException("server speaks protocol v" + welcome.protocolVersion()
                        + ", expected " + HandshakeProtocol.PROTOCOL_VERSION);
            }
            log.info("event=handshake_complete server_instance={} accepted_caps={}",
                    welcome.serverInstanceId(), welcome.acceptedCapabilities());
        } finally {
            pendingHellos.remove(hello.correlationId());
        }
    }

    private void performRegistration(NettyClientTransport transport) throws Exception {
        // Step 1: lean registration — server builds routing table immediately
        var info = new BundleRegistrationInfo(
                parseVersion(config.bundleVersion()),
                config.handlerPayloadTypes());
        transport.send(new Notification(UUID.randomUUID(),
                BundleRegistrationInfo.PAYLOAD_TYPE,
                payloadCodec.encode(info),
                System.currentTimeMillis()))
                .get(config.registrationTimeout().toMillis(),
                        java.util.concurrent.TimeUnit.MILLISECONDS);

        // Step 2: enable if configured for auto-enable, or if the application has
        // already called enable() at least once (reconnect after initial enable).
        if (config.autoEnable() || enabledOnce.get()) {
            transport.send(new Notification(UUID.randomUUID(),
                    ProtocolNotifications.ENABLE, new byte[0],
                    System.currentTimeMillis()))
                    .get(config.registrationTimeout().toMillis(),
                            java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        // Step 3: rich discovery — may be large; sent after routing is live
        var discovery = new BundleDiscoveryInfo(
                parseVersion(config.bundleVersion()),
                config.registeredHandlers(),
                config.payloadInfo());
        transport.send(new Notification(UUID.randomUUID(),
                BundleDiscoveryInfo.PAYLOAD_TYPE,
                payloadCodec.encode(discovery),
                System.currentTimeMillis()))
                .get(config.registrationTimeout().toMillis(),
                        java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private long parseVersion(String version) {
        if (version == null) return 0L;
        try { return Long.parseLong(version); } catch (NumberFormatException e) { return version.hashCode(); }
    }

    private void onMessage(Message message) {
        switch (message) {
            case Welcome w -> {
                var f = pendingHellos.remove(w.correlationId());
                if (f != null) f.complete(w);
            }
            case Reject r -> {
                var f = pendingHellos.remove(r.correlationId());
                if (f != null) {
                    f.completeExceptionally(new IllegalStateException(
                            "server rejected handshake: code=" + r.code() + " reason=" + r.reason()));
                }
            }
            default -> inboundSink.accept(message);
        }
    }

    private boolean transitionTo(BundleClientState expected, BundleClientState next, String reason) {
        if (state.compareAndSet(expected, next)) {
            log.info("event=bundle_state_transition from={} to={} reason={}", expected, next, reason);
            notifyStateChange(expected, next);
            return true;
        }
        return false;
    }

    private void transitionToForce(BundleClientState next, String reason) {
        var prev = state.getAndSet(next);
        if (prev != next) {
            log.info("event=bundle_state_transition from={} to={} reason={} force=true", prev, next, reason);
            notifyStateChange(prev, next);
        }
    }

    private void notifyStateChange(BundleClientState from, BundleClientState to) {
        for (var l : stateListeners) {
            try { l.accept(from, to); }
            catch (Throwable t) { log.warn("event=state_listener_error", t); }
        }
    }
}
