package com.evento.application.client.connection;

import com.evento.application.client.BundleClientConfig;
import com.evento.application.client.BundleClientState;
import com.evento.application.client.handshake.HelloFactory;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.SendFailedException;
import com.evento.transport.Transport;
import com.evento.transport.codec.PayloadCodec;
import com.evento.transport.message.Message;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Reject;
import com.evento.transport.message.Request;
import com.evento.transport.message.Welcome;
import com.evento.transport.netty.NettyClientTransport;
import com.evento.transport.netty.NettyEventLoops;
import com.evento.transport.reconnect.ReconnectStrategy;
import com.evento.transport.protocol.BundleDiscoveryInfo;
import com.evento.transport.protocol.BundleRegistrationInfo;
import com.evento.transport.protocol.ProtocolNotifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
 * {@code CLOSING} and {@code CLOSED} are final: once {@link #stop} has begun no
 * session can move the state anywhere else, so a stop that races a reconnect
 * cannot leave a supervisor running behind it.
 *
 * <p><b>Endpoint failover.</b> The config carries an ordered list of brokers,
 * most preferred first. A session that fails before reaching READY (refused
 * connection, handshake timeout, a broker that fails the identity checks)
 * moves the next attempt to the next endpoint, wrapping around. A session that
 * was READY and then dropped restarts from endpoint 0. The backoff counts
 * <em>passes over the list</em>, not single dials, so trying the second broker
 * after the first refused costs one base delay, not an exponentially growing
 * one; with a single endpoint this is exactly the pre-2.5 behaviour.
 *
 * <p><b>Fail-back (N16).</b> While a session is READY on any endpoint but 0,
 * a background thread probes endpoint 0 every
 * {@link BundleClientConfig#failbackProbeInterval()}. The probe is a TCP
 * connect plus a transport-level {@code Ping} answered by the broker's
 * heartbeat handler, on a separate connection that never sends {@code Hello}:
 * a broker registers a bundle, publishes {@code NodeJoined} and replays its
 * buffered replies only on an accepted Hello, so the probe cannot register
 * anything, steal a reply buffer or supersede a session. A broker whose
 * application is not ready yet leaves the Ping unanswered. Once every probe has
 * answered for the stability window (plus per-instance jitter; a bundle that
 * handles no requests waits for the whole jitter range on top, so the handlers
 * of a cell tend to be back before its callers), the bundle switches
 * gracefully: new outbound requests refused with {@link SendFailedException}
 * (definitely not sent, safe to retry), {@code evento:disable} on the current
 * broker, a bounded wait for the requests in flight in both directions, then
 * the session is closed and endpoint 0 dialled at once. Notifications still go
 * out during the wait, except {@code evento:enable}, which is deferred to the
 * registration on endpoint 0.
 *
 * <p>A request that this broker answers with "no handler" while the latest
 * probe of endpoint 0 was answered switches at once, without waiting for the
 * rest of the window: the handlers it needs have most likely moved back
 * already, and every further request here would fail the same way.
 *
 * <p>The identity checks run on the switch's handshake as on any other; a
 * failed switch lands the bundle on the next endpoint through the normal
 * rotation and doubles the window of the next attempt (up to 16x). A broker
 * that answers the probe but refuses the bundle
 * ({@link BrokerRefusedException}) {@value #MAX_FAILBACK_REFUSALS} times in a
 * row suspends fail-back until a session on endpoint 0 succeeds by other means
 * (the current session dropping) or the process restarts, so a misconfigured
 * preferred broker does not cost an outage every few minutes forever.
 *
 * <p>Reconnect is mandatory by design: the supervisor only stops trying when
 * {@link #stop(Duration)} is called. A user that wants give-up-after-N
 * semantics injects a bounded {@link ReconnectStrategy}.
 */
public final class ConnectionSupervisor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConnectionSupervisor.class);

    /** Consecutive refused switches after which fail-back is suspended. */
    public static final int MAX_FAILBACK_REFUSALS = 3;

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

    /** Index into {@code config.endpoints()} of the broker the next session dials. */
    private final java.util.concurrent.atomic.AtomicInteger endpointIndex =
            new java.util.concurrent.atomic.AtomicInteger();
    /** The broker of the current (or last) session that passed the handshake; null before the first. */
    private volatile String serverInstanceId;
    /** The capabilities the broker of the current (or last) session accepted. */
    private volatile java.util.Set<String> acceptedCapabilities = java.util.Set.of();
    private volatile BundleClientConfig.Endpoint currentEndpoint;
    private final CopyOnWriteArrayList<Consumer<String>> brokerListeners = new CopyOnWriteArrayList<>();

    // ---- fail-back (N16) ----
    /** Endpoint index of the session that is READY right now; -1 when there is none. */
    private volatile int readyEndpointIndex = -1;
    /** Set by the fail-back thread right before it closes the session; consumed by the next session. */
    private final java.util.concurrent.atomic.AtomicBoolean failbackRequested =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /** While true, {@link #send} refuses new requests: the session is being drained. */
    private volatile boolean draining;
    /**
     * Orders {@code evento:enable} against the start of a drain: an enable either
     * goes out before the drain's disable (and is overridden by it) or is
     * deferred to the next session — never re-enables the broker being left.
     */
    private final Object drainGate = new Object();
    private volatile long failbackStartedAtMs;
    private volatile BundleClientConfig.Endpoint failbackFrom;
    /** Consecutive switches that did not end READY on endpoint 0; doubles the next window. */
    private volatile int failbackFailures;
    /** Consecutive switches that endpoint 0's broker refused (identity, protocol, Reject). */
    private volatile int failbackRefusals;
    /** Set after {@link #MAX_FAILBACK_REFUSALS} refusals; cleared by any READY session on endpoint 0. */
    private volatile boolean failbackSuspended;
    /** Until when a "no handler" reply lets a healthy probe switch at once; 0 when not armed. */
    private volatile long reactiveFailbackUntilMs;
    /** Wakes the fail-back thread when a "no handler" reply arrives. */
    private final Semaphore failbackWakeup = new Semaphore(0);
    private final java.util.concurrent.atomic.AtomicLong failbacksCompleted = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong failbacksFailed = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong reactiveFailbacks = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong probesAnswered = new java.util.concurrent.atomic.AtomicLong();
    private volatile java.util.function.IntSupplier inboundInFlight = () -> 0;
    private volatile java.util.function.IntSupplier outboundPending = () -> 0;
    private volatile Thread failbackThread;
    private volatile io.netty.channel.EventLoopGroup probeGroup;
    private final Object probeLock = new Object();

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
     * The {@code serverInstanceId} announced by the broker of the current
     * session, or of the last one that passed the handshake; null before any.
     */
    public String serverInstanceId() { return serverInstanceId; }

    /** Whether the broker of the current session accepted {@code capability} in its Welcome. */
    public boolean accepted(String capability) {
        return acceptedCapabilities.contains(capability);
    }

    /** The endpoint of the current (or last) session; null before the first dial. */
    public BundleClientConfig.Endpoint currentEndpoint() { return currentEndpoint; }

    /**
     * Called with the broker's {@code serverInstanceId} every time a session
     * passes the handshake (identity checks included), before the bundle
     * registers and becomes READY — i.e. before any request can be sent on the
     * new session.
     */
    public void onBrokerAccepted(Consumer<String> listener) {
        brokerListeners.add(listener);
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
     * Send {@code message} on the current transport. Throws
     * {@link SendFailedException} if the bundle is not READY, or if it is a
     * {@link Request} and the session is being drained for a fail-back.
     * Replies and notifications still go out during a drain; an
     * {@code evento:enable} is then not sent to the broker being left but
     * deferred to the registration on the next one (the returned future
     * completes at once).
     */
    public CompletableFuture<Void> send(Message message) {
        var snapshot = state.get();
        if (!snapshot.canSend()) {
            throw new SendFailedException("bundle not ready: state=" + snapshot, null);
        }
        if (message instanceof Notification n && ProtocolNotifications.ENABLE.equals(n.payloadType())) {
            synchronized (drainGate) {
                if (draining) {
                    // Enabling the broker being left would bring new work into the drain.
                    // The next session sends enable in its registration (latched below).
                    enabledOnce.set(true);
                    log.info("event=enable_deferred reason=failback_drain "
                            + "- sent by the registration on the preferred broker");
                    return CompletableFuture.completedFuture(null);
                }
                return sendOnCurrentTransport(message, snapshot);
            }
        }
        if (draining && message instanceof Request) {
            // Replies must still go out: they finish the inbound requests the drain
            // is waiting for; notifications are fire-and-forget and add nothing to it.
            // A new request would only extend the drain, or be stranded on the broker
            // the bundle is leaving.
            throw new SendFailedException("bundle failing back to its preferred broker: not sent", null);
        }
        return sendOnCurrentTransport(message, snapshot);
    }

    private CompletableFuture<Void> sendOnCurrentTransport(Message message, BundleClientState snapshot) {
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
        if (config.failbackActive()) {
            failbackThread = Thread.ofVirtual()
                    .name("evento-failback-" + config.instanceId())
                    .start(this::failbackLoop);
        }
        return firstAttempt;
    }

    /**
     * What the fail-back drain waits for: inbound requests being handled and
     * outbound requests awaiting a reply. Both default to zero (no drain).
     */
    public void setDrainCounters(java.util.function.IntSupplier inboundInFlight,
                                 java.util.function.IntSupplier outboundPending) {
        this.inboundInFlight = inboundInFlight == null ? () -> 0 : inboundInFlight;
        this.outboundPending = outboundPending == null ? () -> 0 : outboundPending;
    }

    /** Switches back to endpoint 0 that ended READY there. */
    public long failbacksCompleted() { return failbacksCompleted.get(); }

    /** Switches back to endpoint 0 that did not (the bundle landed elsewhere). */
    public long failbacksFailed() { return failbacksFailed.get(); }

    /** Switches started early because a request found no handler on the current broker. */
    public long reactiveFailbacks() { return reactiveFailbacks.get(); }

    /** Probes of endpoint 0 that the broker answered. */
    public long failbackProbesAnswered() { return probesAnswered.get(); }

    /**
     * Whether fail-back is suspended because endpoint 0's broker refused the
     * bundle {@value #MAX_FAILBACK_REFUSALS} times in a row. Cleared by the
     * next session that becomes READY on endpoint 0.
     */
    public boolean failbackSuspended() { return failbackSuspended; }

    /** Whether the current session is being drained for a fail-back. */
    public boolean failbackDraining() { return draining; }

    /**
     * A request sent on the current session came back with "no handler" from
     * the broker. If the bundle is away from endpoint 0 and the latest probe of
     * endpoint 0 was answered (or the next one is, shortly), switch now instead
     * of waiting out the stability window: in a cell whose handlers have already
     * moved back to endpoint 0 this broker may have no other handler, and every
     * request would fail the same way until the window ends. Never probes on its
     * own; does nothing after a failed switch (the doubled window applies) or
     * while fail-back is suspended.
     */
    public void noHandlerOnCurrentBroker(String payloadType) {
        if (!config.failbackActive() || failbackSuspended || failbackFailures > 0) return;
        if (state.get() != BundleClientState.READY || readyEndpointIndex <= 0) return;
        long now = System.currentTimeMillis();
        boolean wasArmed = now < reactiveFailbackUntilMs;
        reactiveFailbackUntilMs = now + Math.max(2 * config.failbackProbeInterval().toMillis(), 1_000L);
        if (!wasArmed) {
            log.info("event=failback_reactive_armed payload_type={} current={} "
                    + "- no handler here; moving back as soon as the preferred broker answers a probe",
                    payloadType, currentEndpoint);
        }
        failbackWakeup.release();
    }

    /**
     * Initiate graceful shutdown. Blocks up to {@code deadline} for in-flight
     * sends to drain, then closes the transport.
     */
    public void stop(Duration deadline) {
        BundleClientState prev;
        while (true) {
            prev = state.get();
            if (prev == BundleClientState.CLOSED) {
                releaseFailbackResources();
                return;
            }
            if (prev == BundleClientState.CLOSING || state.compareAndSet(prev, BundleClientState.CLOSING)) break;
        }
        if (prev != BundleClientState.CLOSING) notifyStateChange(prev, BundleClientState.CLOSING);

        // Wake the reconnect loop so it observes CLOSING and exits.
        var thread = supervisorThread;
        if (thread != null) thread.interrupt();
        var fb = failbackThread;
        if (fb != null) fb.interrupt();

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
        releaseFailbackResources();
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
        var endpoints = config.endpoints();
        int n = endpoints.size();
        int attempt = 0;
        try {
            while (!stopping()) {
                attempt++;
                // Passes over the endpoint list, 1-based: what the backoff and the
                // give-up decision count. Equal to `attempt` with one endpoint.
                int pass = (attempt + n - 1) / n;
                int index = Math.floorMod(endpointIndex.get(), n);
                var endpoint = endpoints.get(index);
                try {
                    runOneSession(firstAttempt, endpoint, index);
                    attempt = 0;  // a successful session resets the backoff counter
                    pass = 0;
                    // After a READY session drops, the preferred broker is dialled
                    // first again, even if this session was on a fallback.
                    endpointIndex.set(0);
                } catch (Throwable t) {
                    if (t instanceof InterruptedException) {
                        // Only stop() interrupts this thread.
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (stopping()) break;
                    log.warn("event=supervisor_session_failed attempt={} endpoint={} cause={}",
                            attempt, endpoint, t.toString());
                    if (failbackRequested.getAndSet(false)) {
                        // The switch closed a healthy session and endpoint 0 did not take
                        // the bundle (identity refused, died in between). The rotation
                        // below lands it elsewhere; the next attempt waits longer.
                        onFailbackFailed(endpoint, t);
                    }
                    endpointIndex.set(Math.floorMod(endpointIndex.get() + 1, n));
                    // start() fails only once every endpoint has been tried: a bundle
                    // whose preferred broker is down at boot but whose second one is
                    // healthy must start, not crash.
                    if (!firstAttempt.isDone() && attempt == n) {
                        firstAttempt.completeExceptionally(t);
                    }
                    if (attempt % n == 0 && reconnectStrategy.shouldGiveUp(pass)) {
                        log.error("event=supervisor_gave_up attempt={} passes={}", attempt, pass);
                        transitionToForce(BundleClientState.CLOSED, "give_up");
                        return;
                    }
                }
                // stop() may have run at any point above: CLOSING wins over the
                // transition below (it is refused), and the redial never starts.
                if (stopping()) break;
                if (!transitionToForce(BundleClientState.RECONNECTING, "post_session")) break;
                // A deliberate fail-back dials endpoint 0 at once: it was probed healthy
                // a moment ago, and every millisecond here is a gap in service. The loop
                // condition re-checks stopping() first.
                if (failbackRequested.get()) continue;
                var delay = reconnectStrategy.nextDelay(pass);
                try { Thread.sleep(delay.toMillis()); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            transitionToForce(BundleClientState.CLOSED, "loop_exit");
            if (!firstAttempt.isDone()) {
                firstAttempt.completeExceptionally(
                        new IllegalStateException("supervisor stopped before the first session was READY"));
            }
            // A give-up or a stop: nothing will probe again.
            releaseFailbackResources();
        }
    }

    /**
     * Move the bundle state for the running session; throws if {@link #stop}
     * has begun, which ends the session and the run loop.
     */
    private void advance(BundleClientState next, String reason) {
        if (!transitionToForce(next, reason)) {
            throw new CancellationException("supervisor stopping: not moving to " + next);
        }
    }

    /**
     * Open a fresh transport, perform handshake + register, complete the
     * {@code firstAttempt} future as soon as the session reaches READY (so
     * callers don't have to wait for the transport to later drop), then block
     * here until the transport drops — at which point the run loop decides
     * whether to reconnect.
     */
    private void runOneSession(CompletableFuture<Void> firstAttempt,
                               BundleClientConfig.Endpoint endpoint, int index) throws Exception {
        advance(BundleClientState.CONNECTING, "open_transport");
        // A drain belongs to the session it was started on; the new one sends freely
        // (nothing can be sent before READY anyway).
        draining = false;
        currentEndpoint = endpoint;
        var transport = new NettyClientTransport(
                config.bundleId() + "-" + config.instanceId(),
                endpoint.host(), endpoint.port(),
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
            if (stopping()) {
                // stop() ran between the transition above and the publication of this
                // transport, so it could not close it.
                throw new CancellationException("supervisor stopping");
            }
            transport.connect().get(config.handshakeTimeout().toMillis(), TimeUnit.MILLISECONDS);

            advance(BundleClientState.HANDSHAKING, "tcp_up");
            performHandshake(transport, endpoint);

            advance(BundleClientState.REGISTERING, "after_welcome");
            boolean enableSent = performRegistration(transport);

            readyEndpointIndex = index;
            advance(BundleClientState.READY, "registered");
            if (!enableSent && enabledOnce.get()) {
                // enable() raced the registration: it latched after performRegistration
                // looked, and saw a state that could not send, so it left the send to this
                // session. Sending twice is harmless (enable is idempotent).
                try {
                    transport.send(new Notification(UUID.randomUUID(), ProtocolNotifications.ENABLE,
                            new byte[0], System.currentTimeMillis()));
                } catch (SendFailedException e) {
                    log.debug("event=late_enable_not_sent cause={}", e.toString());
                }
            }
            onReady(index, endpoint);

            if (!firstAttempt.isDone()) firstAttempt.complete(null);
            sessionEnded.get();  // block until the transport drops
        } finally {
            readyEndpointIndex = -1;
            currentTransport.compareAndSet(transport, null);
            try { transport.close(); } catch (Throwable ignored) {}
        }
    }

    /** Fail-back bookkeeping for a session that just became READY on {@code index}. */
    private void onReady(int index, BundleClientConfig.Endpoint endpoint) {
        boolean requested = failbackRequested.getAndSet(false);
        if (index == 0) {
            // Home, by a switch or by an ordinary reconnect: whatever refused the bundle
            // before does not any more, so the back-off and a suspension end here.
            failbackFailures = 0;
            failbackRefusals = 0;
            if (failbackSuspended) {
                failbackSuspended = false;
                log.info("event=failback_resumed endpoint={} server_instance={} "
                        + "- the preferred broker accepted this bundle again", endpoint, serverInstanceId);
            }
            if (requested) {
                failbacksCompleted.incrementAndGet();
                log.info("event=failback_completed from={} to={} server_instance={} took_ms={}",
                        failbackFrom, endpoint, serverInstanceId,
                        System.currentTimeMillis() - failbackStartedAtMs);
            }
        } else if (requested) {
            failbackFailures++;
            failbacksFailed.incrementAndGet();
            log.warn("event=failback_failed from={} to={} landed_on={} consecutive_failures={}",
                    failbackFrom, config.endpoints().getFirst(), endpoint, failbackFailures);
        }
    }

    /** A switch whose session on endpoint 0 failed before READY. */
    private void onFailbackFailed(BundleClientConfig.Endpoint endpoint, Throwable cause) {
        failbackFailures++;
        failbacksFailed.incrementAndGet();
        boolean refused = isRefusal(cause);
        if (refused) {
            failbackRefusals++;
        } else {
            failbackRefusals = 0;
        }
        log.warn("event=failback_failed from={} to={} cause={} refused={} consecutive_failures={} "
                        + "- continuing on the next endpoint",
                failbackFrom, endpoint, cause.toString(), refused, failbackFailures);
        if (refused && failbackRefusals >= MAX_FAILBACK_REFUSALS && !failbackSuspended) {
            failbackSuspended = true;
            log.error("event=failback_suspended endpoint={} consecutive_refusals={} cause={} "
                            + "- the preferred broker answers but refuses this bundle; not moving back to it "
                            + "until a session there succeeds (after the current one drops) or the bundle restarts. "
                            + "Check its evento.cluster.name, server instance id and auth token",
                    endpoint, failbackRefusals, cause.toString());
        }
    }

    private static boolean isRefusal(Throwable t) {
        for (var c = t; c != null; c = c.getCause()) {
            if (c instanceof BrokerRefusedException) return true;
            if (c.getCause() == c) break;
        }
        return false;
    }

    // ---------- fail-back (N16) ----------

    private boolean stopping() {
        var s = state.get();
        return s.isTerminal() || s == BundleClientState.CLOSING;
    }

    /**
     * Probe endpoint 0 while READY elsewhere; switch once it has answered every
     * probe for the required time, or at once when a "no handler" reply arrives
     * while it is answering. Runs on its own virtual thread for the life of the
     * supervisor.
     */
    private void failbackLoop() {
        var preferred = config.endpoints().getFirst();
        long intervalMs = config.failbackProbeInterval().toMillis();
        long healthySince = -1L;
        long requiredMs = 0L;
        boolean lastProbeFailed = true;
        long nextProbeAt = System.currentTimeMillis() + intervalMs;
        while (!stopping()) {
            // Wakes keep the probe schedule: a steady stream of "no handler" replies
            // must neither starve the probe nor speed it up.
            boolean woken;
            try {
                long waitMs = nextProbeAt - System.currentTimeMillis();
                woken = waitMs > 0 && failbackWakeup.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return;
            }
            if (woken) {
                failbackWakeup.drainPermits();
            } else {
                nextProbeAt = System.currentTimeMillis() + intervalMs;
            }
            if (stopping()) return;
            int at = readyEndpointIndex;
            if (state.get() != BundleClientState.READY || at <= 0 || failbackSuspended) {
                healthySince = -1L;
                lastProbeFailed = true;
                continue;
            }
            long now = System.currentTimeMillis();
            boolean reactive = reactiveArmed(now);
            boolean healthy;
            if (woken) {
                // A "no handler" reply: acts on a streak that is already healthy, and
                // never probes by itself (a burst of failing requests must not become a
                // burst of probes).
                if (!(reactive && healthySince >= 0)) continue;
                healthy = true;
            } else {
                healthy = probe(preferred, lastProbeFailed);
                lastProbeFailed = !healthy;
                now = System.currentTimeMillis();
                nextProbeAt = now + intervalMs;
                if (stopping()) return;
            }
            if (!healthy) {
                if (healthySince >= 0) {
                    log.info("event=failback_probe_unhealthy endpoint={} healthy_for_ms={} "
                            + "- stability window restarts", preferred, now - healthySince);
                }
                healthySince = -1L;
                continue;
            }
            if (healthySince < 0) {
                healthySince = now;
                requiredMs = requiredHealthyMs();
                log.info("event=failback_probe_healthy endpoint={} current={} failback_after_ms={}",
                        preferred, currentEndpoint, requiredMs);
            }
            boolean due = now - healthySince >= requiredMs;
            if (!due && reactive) {
                due = true;
                reactiveFailbacks.incrementAndGet();
                log.info("event=failback_reactive endpoint={} current={} healthy_for_ms={} "
                                + "- a request found no handler on this broker while the preferred one answers; "
                                + "moving back without waiting for the window",
                        preferred, currentEndpoint, now - healthySince);
            }
            if (!due) continue;
            healthySince = -1L;
            reactiveFailbackUntilMs = 0L;
            try {
                performFailback(preferred);
            } catch (InterruptedException e) {
                // stop(): nothing to undo, the session is being closed anyway.
                return;
            } catch (Throwable t) {
                log.warn("event=failback_error endpoint={} cause={}", preferred, t.toString());
                abortDrain();
            }
        }
    }

    private boolean reactiveArmed(long now) {
        return now < reactiveFailbackUntilMs && failbackFailures == 0 && !failbackSuspended;
    }

    /**
     * A switch that stopped half-way leaves the session disabled and draining:
     * serve again, and re-enable if the bundle was enabled.
     */
    private void abortDrain() {
        synchronized (drainGate) {
            draining = false;
        }
        var t = currentTransport.get();
        if (t == null || state.get() != BundleClientState.READY) return;
        if (config.autoEnable() || enabledOnce.get()) {
            try {
                t.send(new Notification(UUID.randomUUID(), ProtocolNotifications.ENABLE,
                        new byte[0], System.currentTimeMillis()));
            } catch (Exception e) {
                log.warn("event=failback_reenable_failed cause={}", e.toString());
            }
        }
    }

    /**
     * The stability window, doubled per consecutive failed switch (up to 16x),
     * plus a fresh random jitter. A bundle that handles no requests (a pure
     * caller, e.g. a gateway) adds the whole jitter range on top, which puts it
     * after every handler that started its window at the same time: the
     * handlers of a cell tend to be back on the preferred broker before its
     * callers. That is all the ordering needs; a caller that moves too late sees
     * "no handler" on the broker it is on and switches at once
     * ({@link #noHandlerOnCurrentBroker}).
     */
    private long requiredHealthyMs() {
        long window = config.failbackStabilityWindow().toMillis();
        long base = window << Math.min(failbackFailures, 4);
        long jitterMax = config.failbackJitter().toMillis();
        long jitter = jitterMax <= 0 ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextLong(jitterMax + 1);
        long callersLast = config.handlerPayloadTypes().isEmpty() ? jitterMax : 0;
        return base + jitter + callersLast;
    }

    /**
     * Is endpoint 0 an Evento broker that answers? TCP connect plus a
     * {@code Ping} that the broker's heartbeat handler answers with a
     * {@code Pong} below the message router: no {@code Hello}, so nothing is
     * registered, no {@code NodeJoined} is published, no buffered reply is
     * replayed and no live session is superseded. Answering through the
     * Evento pipeline (not just an open port) matters when the endpoint is a
     * proxy or a VIP that accepts TCP while the broker behind it is dead; and a
     * broker whose application is still starting leaves the Ping unanswered.
     *
     * @param quietPrecheck first try a plain socket connect, so that a broker
     *                      that is down does not produce a transport WARN line
     *                      per probe for as long as it stays down
     */
    private boolean probe(BundleClientConfig.Endpoint endpoint, boolean quietPrecheck) {
        long timeoutMs = Math.max(100L, Math.min(config.handshakeTimeout().toMillis(),
                config.failbackProbeInterval().toMillis()));
        if (quietPrecheck) {
            try (var s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(endpoint.host(), endpoint.port()), (int) timeoutMs);
            } catch (Exception e) {
                log.debug("event=failback_probe_refused endpoint={} cause={}", endpoint, e.toString());
                return false;
            }
        }
        var group = probeGroup();
        if (group == null) return false;
        var transport = new NettyClientTransport(
                config.bundleId() + "-" + config.instanceId() + "-failback-probe",
                endpoint.host(), endpoint.port(), config.transportConfig(), group)
                .quietLifecycleLogging();
        var pingId = UUID.randomUUID();
        var pong = new CompletableFuture<Void>();
        transport.onMessage(m -> {
            if (m instanceof com.evento.transport.message.Pong p && pingId.equals(p.correlationId())) {
                pong.complete(null);
            }
        });
        // Never give up on the connect before Netty does: a connect abandoned early
        // could complete after this probe had moved on. close() aborts it anyway, but
        // waiting for the transport's own connect timeout keeps probes serial.
        long connectWaitMs = Math.max(timeoutMs, config.transportConfig().connectTimeout().toMillis() + 500L);
        try {
            transport.connect().get(connectWaitMs, TimeUnit.MILLISECONDS);
            transport.send(new com.evento.transport.message.Ping(pingId, 0L, System.currentTimeMillis()))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            pong.get(timeoutMs, TimeUnit.MILLISECONDS);
            probesAnswered.incrementAndGet();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.debug("event=failback_probe_failed endpoint={} cause={}", endpoint, e.toString());
            return false;
        } finally {
            try { transport.close(); } catch (Throwable ignored) {}
        }
    }

    /** One small event loop shared by all probes of this supervisor, created on first use. */
    private io.netty.channel.EventLoopGroup probeGroup() {
        synchronized (probeLock) {
            if (stopping()) return null;
            if (probeGroup == null) {
                probeGroup = NettyEventLoops.newNioEventLoopGroup(1, r -> {
                    var t = new Thread(r, "evento-failback-probe-" + config.instanceId());
                    t.setDaemon(true);
                    return t;
                });
            }
            return probeGroup;
        }
    }

    /** Stop the fail-back thread and release the probe event loop; idempotent. */
    private void releaseFailbackResources() {
        var fb = failbackThread;
        if (fb != null && fb != Thread.currentThread()) fb.interrupt();
        synchronized (probeLock) {
            var group = probeGroup;
            probeGroup = null;
            if (group != null) group.shutdownGracefully(0, 200, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Leave the current broker gracefully and dial endpoint 0: refuse new
     * outbound requests, disable here, wait (bounded) for what is in flight,
     * close. The run loop sees the session end with {@link #failbackRequested}
     * set and dials endpoint 0 without backoff.
     */
    private void performFailback(BundleClientConfig.Endpoint preferred) throws InterruptedException {
        var transport = currentTransport.get();
        if (transport == null || state.get() != BundleClientState.READY || readyEndpointIndex <= 0) return;
        var from = currentEndpoint;
        long started = System.currentTimeMillis();
        long drainMs = config.failbackDrainTimeout().toMillis();
        log.info("event=failback_started from={} from_instance={} to={} drain_timeout_ms={} "
                        + "inbound_in_flight={} outbound_pending={}",
                from, serverInstanceId, preferred, drainMs,
                inboundInFlight.getAsInt(), outboundPending.getAsInt());

        // 1. New outbound requests fail fast with SendFailedException (definitely not
        //    sent) instead of joining a drain they could extend indefinitely. First, so
        //    that an enable() racing the switch is either written before the disable
        //    below (which then wins) or deferred to the next session.
        synchronized (drainGate) {
            draining = true;
        }

        // 2. Out of the current broker's available view: it stops picking this
        //    bundle for new requests. The session and its routes stay up, so the
        //    replies to what is already in flight still find their way.
        try {
            transport.send(new Notification(UUID.randomUUID(), ProtocolNotifications.DISABLE,
                    new byte[0], System.currentTimeMillis()))
                    .get(config.registrationTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("event=failback_disable_failed from={} cause={}", from, e.toString());
        }

        // 3. Drain. The short grace covers requests the broker picked this bundle for
        //    just before it processed the disable and that are still on the wire.
        long graceEnd = started + Math.min(250L, drainMs);
        long deadline = started + drainMs;
        int in, out;
        while (true) {
            in = inboundInFlight.getAsInt();
            out = outboundPending.getAsInt();
            long now = System.currentTimeMillis();
            if (currentTransport.get() != transport || state.get() != BundleClientState.READY) {
                draining = false;
                log.info("event=failback_aborted from={} reason=session_ended_during_drain", from);
                return;
            }
            if ((in == 0 && out == 0 && now >= graceEnd) || now >= deadline) break;
            Thread.sleep(10L);
        }
        long waited = System.currentTimeMillis() - started;
        if (in > 0 || out > 0) {
            log.warn("event=failback_drain_timeout from={} waited_ms={} inbound_in_flight={} outbound_pending={} "
                    + "- outbound requests still pending fail with BrokerChangedException on the new broker",
                    from, waited, in, out);
        } else {
            log.info("event=failback_drained from={} waited_ms={}", from, waited);
        }

        // 4. Switch.
        failbackFrom = from;
        failbackStartedAtMs = started;
        endpointIndex.set(0);
        failbackRequested.set(true);
        if (currentTransport.get() != transport || state.get() != BundleClientState.READY) {
            // The session ended on its own after the last drain check: the run loop is
            // already dialling (or on) another session, which must not take this
            // request for its own and count as a fail-back.
            failbackRequested.set(false);
            draining = false;
            log.info("event=failback_aborted from={} reason=session_ended_before_switch", from);
            return;
        }
        try { transport.close(); } catch (Throwable ignored) {}
    }

    private void performHandshake(NettyClientTransport transport,
                                  BundleClientConfig.Endpoint endpoint) throws Exception {
        var hello = HelloFactory.build(config);
        var helloFuture = new CompletableFuture<Welcome>();
        pendingHellos.put(hello.correlationId(), helloFuture);
        try {
            transport.send(hello).get(config.handshakeTimeout().toMillis(), TimeUnit.MILLISECONDS);
            var welcome = helloFuture.get(config.handshakeTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (welcome.protocolVersion() != HandshakeProtocol.PROTOCOL_VERSION) {
                throw new BrokerRefusedException("server speaks protocol v" + welcome.protocolVersion()
                        + ", expected " + HandshakeProtocol.PROTOCOL_VERSION);
            }
            // Thrown here, before registration, so the run loop treats a wrong broker
            // exactly like an unreachable one: log, back off, dial again. A bundle
            // that registered with the wrong broker would look perfectly healthy
            // from its own logs while every command it handled was persisted
            // somewhere else — the failure has to be loud and has to be here.
            var expected = config.expectedServerInstanceId();
            if (expected != null && !expected.equals(welcome.serverInstanceId())) {
                log.error("event=server_instance_mismatch host={} port={} announced={} expected={} "
                                + "- refusing to register with this broker",
                        endpoint.host(), endpoint.port(), welcome.serverInstanceId(), expected);
                throw new BrokerRefusedException("broker at " + endpoint
                        + " announced instance '" + welcome.serverInstanceId()
                        + "', expected '" + expected + "'");
            }
            // Same rule for the cluster: with an endpoint list every broker has its
            // own instance id, so the cluster name is the identity they share. An
            // empty announcement (older broker, or evento.cluster.name unset) cannot
            // prove membership and is refused like a wrong one.
            var expectedCluster = config.expectedClusterName();
            if (expectedCluster != null && !expectedCluster.equals(welcome.clusterName())) {
                log.error("event=cluster_name_mismatch host={} port={} announced={} expected={} "
                                + "- refusing to register with this broker",
                        endpoint.host(), endpoint.port(),
                        welcome.clusterName().isEmpty() ? "<none>" : welcome.clusterName(), expectedCluster);
                throw new BrokerRefusedException("broker at " + endpoint
                        + " announced cluster '" + welcome.clusterName()
                        + "', expected '" + expectedCluster + "'");
            }
            log.info("event=handshake_complete server_instance={} cluster={} endpoint={} accepted_caps={}",
                    welcome.serverInstanceId(), welcome.clusterName(), endpoint,
                    welcome.acceptedCapabilities());
            serverInstanceId = welcome.serverInstanceId();
            acceptedCapabilities = welcome.acceptedCapabilities();
            for (var l : brokerListeners) {
                try { l.accept(welcome.serverInstanceId()); }
                catch (Throwable t) { log.warn("event=broker_listener_error", t); }
            }
        } finally {
            pendingHellos.remove(hello.correlationId());
        }
    }

    /** @return whether {@code evento:enable} was sent on this session */
    private boolean performRegistration(NettyClientTransport transport) throws Exception {
        // Step 1: lean registration — server builds routing table immediately
        var info = new BundleRegistrationInfo(
                parseVersion(config.bundleVersion()),
                config.handlerPayloadTypes(),
                config.queryPayloadTypes());
        transport.send(new Notification(UUID.randomUUID(),
                BundleRegistrationInfo.PAYLOAD_TYPE,
                payloadCodec.encode(info),
                System.currentTimeMillis()))
                .get(config.registrationTimeout().toMillis(), TimeUnit.MILLISECONDS);

        // Step 2: enable if configured for auto-enable, or if the application has
        // already called enable() at least once (reconnect after initial enable).
        boolean enable = config.autoEnable() || enabledOnce.get();
        if (enable) {
            transport.send(new Notification(UUID.randomUUID(),
                    ProtocolNotifications.ENABLE, new byte[0],
                    System.currentTimeMillis()))
                    .get(config.registrationTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        // Step 3: rich discovery — may be large; sent after routing is live
        var discovery = new BundleDiscoveryInfo(
                parseVersion(config.bundleVersion()),
                config.description(),
                config.detail(),
                config.repositoryUrl(),
                config.linePrefix(),
                config.registeredHandlers(),
                config.payloadInfo());
        transport.send(new Notification(UUID.randomUUID(),
                BundleDiscoveryInfo.PAYLOAD_TYPE,
                payloadCodec.encode(discovery),
                System.currentTimeMillis()))
                .get(config.registrationTimeout().toMillis(), TimeUnit.MILLISECONDS);
        return enable;
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
                    f.completeExceptionally(new BrokerRefusedException(
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

    /**
     * Move to {@code next} from whatever state the bundle is in — except out of
     * {@code CLOSING} (only to {@code CLOSED}) or {@code CLOSED}: once {@link #stop}
     * has begun a session must not overwrite it, or the loop that owns the
     * session would never see it and keep reconnecting behind a stopped bundle.
     *
     * @return false if the transition was refused because the supervisor is stopping
     */
    private boolean transitionToForce(BundleClientState next, String reason) {
        while (true) {
            var prev = state.get();
            if (prev == next) return true;
            if (prev == BundleClientState.CLOSED) return false;
            if (prev == BundleClientState.CLOSING && next != BundleClientState.CLOSED) return false;
            if (state.compareAndSet(prev, next)) {
                log.info("event=bundle_state_transition from={} to={} reason={} force=true", prev, next, reason);
                notifyStateChange(prev, next);
                return true;
            }
        }
    }

    private void notifyStateChange(BundleClientState from, BundleClientState to) {
        for (var l : stateListeners) {
            try { l.accept(from, to); }
            catch (Throwable t) { log.warn("event=state_listener_error", t); }
        }
    }
}
