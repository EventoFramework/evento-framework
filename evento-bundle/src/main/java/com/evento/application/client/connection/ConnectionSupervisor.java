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
 * anything, steal a reply buffer or supersede a session. Once every probe has
 * answered for the stability window (plus per-instance jitter; a bundle that
 * handles no requests waits one more window so the handlers of a cell are
 * back before its callers), the bundle switches gracefully:
 * {@code evento:disable} on the current broker, new outbound requests refused
 * with {@link SendFailedException} (definitely not sent, safe to retry), a
 * bounded wait for the requests in flight in both directions, then the
 * session is closed and endpoint 0 dialled at once. The identity checks run
 * on that handshake as on any other; a failed switch lands the bundle on the
 * next endpoint through the normal rotation and doubles the window of the
 * next attempt (up to 16x), so a broker that is up but refuses the bundle
 * cannot cause ping-pong.
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

    /** Index into {@code config.endpoints()} of the broker the next session dials. */
    private final java.util.concurrent.atomic.AtomicInteger endpointIndex =
            new java.util.concurrent.atomic.AtomicInteger();
    /** The broker of the current (or last) session that passed the handshake; null before the first. */
    private volatile String serverInstanceId;
    private volatile BundleClientConfig.Endpoint currentEndpoint;
    private final CopyOnWriteArrayList<Consumer<String>> brokerListeners = new CopyOnWriteArrayList<>();

    // ---- fail-back (N16) ----
    /** Endpoint index of the session that is READY right now; -1 when there is none. */
    private volatile int readyEndpointIndex = -1;
    /** Set by the fail-back thread right before it closes the session; consumed by the next session. */
    private final java.util.concurrent.atomic.AtomicBoolean failbackRequested =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /** While true, {@link #send} refuses everything but replies: the session is being drained. */
    private volatile boolean draining;
    private volatile long failbackStartedAtMs;
    private volatile BundleClientConfig.Endpoint failbackFrom;
    /** Consecutive switches that did not end READY on endpoint 0; doubles the next window. */
    private volatile int failbackFailures;
    private final java.util.concurrent.atomic.AtomicLong failbacksCompleted = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong failbacksFailed = new java.util.concurrent.atomic.AtomicLong();
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
     * Send {@code message} on the current transport. Throws if the bundle is
     * not READY.
     */
    public CompletableFuture<Void> send(Message message) {
        var snapshot = state.get();
        if (!snapshot.canSend()) {
            throw new SendFailedException("bundle not ready: state=" + snapshot, null);
        }
        if (draining && !(message instanceof com.evento.transport.message.Response)) {
            // Replies must still go out: they finish the inbound requests the
            // drain is waiting for. Anything new would only add to the drain,
            // or be stranded on the broker the bundle is leaving.
            throw new SendFailedException("bundle failing back to its preferred broker: not sent", null);
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

    /** Probes of endpoint 0 that the broker answered. */
    public long failbackProbesAnswered() { return probesAnswered.get(); }

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
        synchronized (probeLock) {
            var group = probeGroup;
            probeGroup = null;
            if (group != null) group.shutdownGracefully(0, 200, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
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
        while (!state.get().isTerminal() && state.get() != BundleClientState.CLOSING) {
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
                log.warn("event=supervisor_session_failed attempt={} endpoint={} cause={}",
                        attempt, endpoint, t.toString());
                if (failbackRequested.getAndSet(false)) {
                    // The switch closed a healthy session and endpoint 0 did not take
                    // the bundle (identity refused, died in between). The rotation
                    // below lands it elsewhere; the next attempt waits longer.
                    failbackFailures++;
                    failbacksFailed.incrementAndGet();
                    log.warn("event=failback_failed from={} to={} cause={} consecutive_failures={} "
                                    + "- continuing on the next endpoint",
                            failbackFrom, endpoint, t.toString(), failbackFailures);
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
            if (state.get() == BundleClientState.CLOSING) break;
            transitionToForce(BundleClientState.RECONNECTING, "post_session");
            // A deliberate fail-back dials endpoint 0 at once: it was probed healthy
            // a moment ago, and every millisecond here is a gap in service.
            if (failbackRequested.get()) continue;
            var delay = reconnectStrategy.nextDelay(pass);
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
    private void runOneSession(CompletableFuture<Void> firstAttempt,
                               BundleClientConfig.Endpoint endpoint, int index) throws Exception {
        transitionToForce(BundleClientState.CONNECTING, "open_transport");
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
            transport.connect().get(config.handshakeTimeout().toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);

            transitionToForce(BundleClientState.HANDSHAKING, "tcp_up");
            performHandshake(transport, endpoint);

            transitionToForce(BundleClientState.REGISTERING, "after_welcome");
            performRegistration(transport);

            readyEndpointIndex = index;
            transitionToForce(BundleClientState.READY, "registered");
            if (failbackRequested.getAndSet(false)) {
                if (index == 0) {
                    failbackFailures = 0;
                    failbacksCompleted.incrementAndGet();
                    log.info("event=failback_completed from={} to={} server_instance={} took_ms={}",
                            failbackFrom, endpoint, serverInstanceId,
                            System.currentTimeMillis() - failbackStartedAtMs);
                } else {
                    failbackFailures++;
                    failbacksFailed.incrementAndGet();
                    log.warn("event=failback_failed from={} to={} landed_on={} consecutive_failures={}",
                            failbackFrom, config.endpoints().getFirst(), endpoint, failbackFailures);
                }
            }

            if (!firstAttempt.isDone()) firstAttempt.complete(null);
            sessionEnded.get();  // block until the transport drops
        } finally {
            readyEndpointIndex = -1;
            currentTransport.compareAndSet(transport, null);
            try { transport.close(); } catch (Throwable ignored) {}
        }
    }

    // ---------- fail-back (N16) ----------

    private boolean stopping() {
        var s = state.get();
        return s.isTerminal() || s == BundleClientState.CLOSING;
    }

    /**
     * Probe endpoint 0 while READY elsewhere; switch once it has answered every
     * probe for the required time. Runs on its own virtual thread for the life
     * of the supervisor.
     */
    private void failbackLoop() {
        var preferred = config.endpoints().getFirst();
        long healthySince = -1L;
        long requiredMs = 0L;
        boolean lastProbeFailed = true;
        while (!stopping()) {
            try {
                Thread.sleep(config.failbackProbeInterval());
            } catch (InterruptedException e) {
                return;
            }
            if (stopping()) return;
            int at = readyEndpointIndex;
            if (state.get() != BundleClientState.READY || at <= 0) {
                healthySince = -1L;
                continue;
            }
            boolean healthy = probe(preferred, lastProbeFailed);
            lastProbeFailed = !healthy;
            long now = System.currentTimeMillis();
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
            if (now - healthySince < requiredMs) continue;
            healthySince = -1L;
            try {
                performFailback(preferred);
            } catch (Throwable t) {
                draining = false;
                log.warn("event=failback_error endpoint={} cause={}", preferred, t.toString());
            }
        }
    }

    /**
     * The stability window, doubled per consecutive failed switch (up to 16x),
     * plus a fresh random jitter; a bundle that handles no requests (a pure
     * caller, e.g. a gateway) adds one more window, so that the handlers of a
     * cell tend to be back on the preferred broker before its callers are.
     */
    private long requiredHealthyMs() {
        long window = config.failbackStabilityWindow().toMillis();
        long base = window << Math.min(failbackFailures, 4);
        long jitterMax = config.failbackJitter().toMillis();
        long jitter = jitterMax <= 0 ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextLong(jitterMax + 1);
        long callersLast = config.handlerPayloadTypes().isEmpty() ? window : 0;
        return base + jitter + callersLast;
    }

    /**
     * Is endpoint 0 an Evento broker that answers? TCP connect plus a
     * {@code Ping} that the broker's heartbeat handler answers with a
     * {@code Pong} below the message router: no {@code Hello}, so nothing is
     * registered, no {@code NodeJoined} is published, no buffered reply is
     * replayed and no live session is superseded. Answering through the
     * Evento pipeline (not just an open port) matters when the endpoint is a
     * proxy or a VIP that accepts TCP while the broker behind it is dead.
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
                endpoint.host(), endpoint.port(), config.transportConfig(), group);
        var pingId = UUID.randomUUID();
        var pong = new CompletableFuture<Void>();
        transport.onMessage(m -> {
            if (m instanceof com.evento.transport.message.Pong p && pingId.equals(p.correlationId())) {
                pong.complete(null);
            }
        });
        try {
            transport.connect().get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            transport.send(new com.evento.transport.message.Ping(pingId, 0L, System.currentTimeMillis()))
                    .get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            pong.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
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
    @SuppressWarnings("deprecation") // NioEventLoopGroup: the API that exists in Netty 4.1 and 4.2
    private io.netty.channel.EventLoopGroup probeGroup() {
        synchronized (probeLock) {
            if (stopping()) return null;
            if (probeGroup == null) {
                probeGroup = new io.netty.channel.nio.NioEventLoopGroup(1, r -> {
                    var t = new Thread(r, "evento-failback-probe-" + config.instanceId());
                    t.setDaemon(true);
                    return t;
                });
            }
            return probeGroup;
        }
    }

    /**
     * Leave the current broker gracefully and dial endpoint 0: disable here,
     * refuse new outbound work, wait (bounded) for what is in flight, close.
     * The run loop sees the session end with {@link #failbackRequested} set and
     * dials endpoint 0 without backoff.
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

        // 1. Out of the current broker's available view: it stops picking this
        //    bundle for new requests. The session and its routes stay up, so the
        //    replies to what is already in flight still find their way.
        try {
            transport.send(new Notification(UUID.randomUUID(), ProtocolNotifications.DISABLE,
                    new byte[0], System.currentTimeMillis()))
                    .get(config.registrationTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("event=failback_disable_failed from={} cause={}", from, e.toString());
        }
        // 2. New outbound requests fail fast with SendFailedException (definitely not
        //    sent) instead of joining a drain they could extend indefinitely.
        draining = true;

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
        try { transport.close(); } catch (Throwable ignored) {}
    }

    private void performHandshake(NettyClientTransport transport,
                                  BundleClientConfig.Endpoint endpoint) throws Exception {
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
                throw new IllegalStateException("broker at " + endpoint
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
                throw new IllegalStateException("broker at " + endpoint
                        + " announced cluster '" + welcome.clusterName()
                        + "', expected '" + expectedCluster + "'");
            }
            log.info("event=handshake_complete server_instance={} cluster={} endpoint={} accepted_caps={}",
                    welcome.serverInstanceId(), welcome.clusterName(), endpoint,
                    welcome.acceptedCapabilities());
            serverInstanceId = welcome.serverInstanceId();
            for (var l : brokerListeners) {
                try { l.accept(welcome.serverInstanceId()); }
                catch (Throwable t) { log.warn("event=broker_listener_error", t); }
            }
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
