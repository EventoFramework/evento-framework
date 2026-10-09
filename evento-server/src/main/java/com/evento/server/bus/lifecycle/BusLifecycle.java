package com.evento.server.bus.lifecycle;

import com.evento.common.utils.FatalErrors;
import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.correlation.ForwardingDedupCache;
import com.evento.server.bus.event.BusEvent;
import com.evento.server.bus.event.BusEventBus;
import com.evento.transport.protocol.BundleDiscoveryInfo;
import com.evento.transport.protocol.BundleRegistrationInfo;
import com.evento.transport.protocol.ProtocolNotifications;
import com.evento.server.bus.handshake.HandshakeHandler;
import com.evento.server.bus.handshake.HandshakeHandler.HandshakeOutcome;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.Connection;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.BundleSession;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.bus.router.MessageRouter;
import com.evento.server.bus.security.TokenValidator;
import com.evento.transport.Frame;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.SendFailedException;
import com.evento.transport.Transport;
import com.evento.transport.TransportServer;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.codec.PayloadCodec;
import com.evento.transport.message.Hello;
import com.evento.transport.message.Message;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import com.evento.transport.message.ResponseError;
import com.evento.transport.state.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Orchestrator for the v2 server bus. Wires the seven small components
 * (registries, correlation store, forwarding table, handshake handler,
 * router, event bus, transport server) into one coherent lifecycle.
 *
 * <p>Constructor takes only collaborators — no side-effects, no static state.
 * Boot happens in {@link #start(int)}; teardown in {@link #stop(Duration)}.
 *
 * <p>Public surface kept small on purpose: the few methods needed by the v1
 * consumers (view snapshots, forward, isBundleAvailable) plus
 * {@link #subscribe(Consumer)} for cluster-event observers.
 */
public final class BusLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BusLifecycle.class);

    // --- protocol-internal notification payload types (forwarded for callers convenience) ---
    public static final String NOTIFY_ENABLE = com.evento.transport.protocol.ProtocolNotifications.ENABLE;
    public static final String NOTIFY_DISABLE = com.evento.transport.protocol.ProtocolNotifications.DISABLE;

    /** {@code Request.sourceBundleId} used by server-initiated forwards. */
    public static final String SERVER_BUNDLE_ID = "evento-server";

    private final TransportServer transportServer;
    private final ConnectionRegistry connectionRegistry;
    private final ClusterRegistry clusterRegistry;
    private final CorrelationStore correlationStore;
    private final ForwardingTable forwardingTable;
    private final BusEventBus eventBus;
    private final HandshakeHandler handshakeHandler;
    private final PayloadCodec payloadCodec;
    private final TokenValidator tokenValidator;
    private final ForwardingDedupCache dedupCache;
    private final MessageRouter router;
    private final String serverInstanceId;
    private final ConcurrentHashMap<Transport, BundleSession> sessionsByTransport = new ConcurrentHashMap<>();

    /**
     * Server-local handlers for request payloadTypes the broker answers directly
     * (e.g. EventFetchRequest, EventLastSequenceNumberRequest) without forwarding
     * to a bundle. Populated after construction via {@link #registerLocalHandler}.
     */
    @FunctionalInterface
    public interface LocalRequestHandler {
        byte[] handle(byte[] payload) throws Exception;
    }

    /**
     * A server-local handler that also needs to know which bundle sent the
     * request — e.g. {@code CommandBrokerHandler}, which forwards the command on
     * to a bundle and wants the pick to honour the originator's zone.
     */
    @FunctionalInterface
    public interface OriginatorAwareRequestHandler {
        byte[] handle(byte[] payload, NodeAddress originator) throws Exception;
    }

    /**
     * This server's monotonic clock in milliseconds ({@link System#nanoTime()} based), on
     * which request deadlines are expressed. Only differences are meaningful; it does not
     * move with wall-clock (NTP) steps.
     */
    public static long monotonicMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    /**
     * A server-local handler that also honours the caller's deadline: {@code deadlineMs}
     * is the {@link #monotonicMillis()} after which the caller no longer waits
     * ({@link Request#deadlineFrom}), or {@link Long#MAX_VALUE} when the caller set no
     * timeout. {@code CommandBrokerHandler} uses it to stop before the lock, the forward
     * and the append of a command nobody waits for any more.
     */
    @FunctionalInterface
    public interface DeadlineAwareRequestHandler {
        byte[] handle(byte[] payload, NodeAddress originator, long deadlineMs) throws Exception;
    }

    private final ConcurrentHashMap<String, DeadlineAwareRequestHandler> localHandlers = new ConcurrentHashMap<>();

    public void registerLocalHandler(String payloadType, LocalRequestHandler handler) {
        localHandlers.put(payloadType, (payload, originator, deadlineMs) -> handler.handle(payload));
    }

    public void registerLocalHandler(String payloadType, OriginatorAwareRequestHandler handler) {
        localHandlers.put(payloadType, (payload, originator, deadlineMs) -> handler.handle(payload, originator));
    }

    public void registerLocalHandler(String payloadType, DeadlineAwareRequestHandler handler) {
        localHandlers.put(payloadType, handler);
    }

    /** Stage label: the request's deadline passed while it waited for this broker to dispatch it. */
    public static final String EXPIRED_STAGE_QUEUED = "queued";
    /** Stage label: the deadline passed while a command waited for its aggregate or resource lock. */
    public static final String EXPIRED_STAGE_LOCK_WAIT = "lock_wait";
    /** Stage label: the deadline passed before a command was forwarded to its handler bundle. */
    public static final String EXPIRED_STAGE_BEFORE_FORWARD = "before_forward";
    /**
     * Stage label: the deadline passed while the handler bundle worked on an aggregate
     * command (it did not answer in time, or answered late); the broker appended nothing for
     * it. Never used for service commands, whose handlers may have had side effects: their
     * event is stored whenever the handler answers.
     */
    public static final String EXPIRED_STAGE_BEFORE_APPEND = "before_append";
    /** Every stage label, for metrics binding. */
    public static final java.util.List<String> EXPIRED_STAGES = java.util.List.of(
            EXPIRED_STAGE_QUEUED, EXPIRED_STAGE_LOCK_WAIT, EXPIRED_STAGE_BEFORE_FORWARD,
            EXPIRED_STAGE_BEFORE_APPEND);

    private final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> expiredDropped =
            new ConcurrentHashMap<>();

    /**
     * Records that a request was dropped because its caller's deadline had passed, logs
     * {@code event=request_expired_dropped} and returns the exception to answer it with.
     * Callers must not have forwarded, locked-and-appended or otherwise executed it.
     */
    public com.evento.transport.RequestExpiredException expiredDrop(String stage, String payloadType,
                                                                    Object correlationId, long deadlineMs) {
        long overdueMs = monotonicMillis() - deadlineMs;
        expiredDropped.computeIfAbsent(stage, k -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
        log.warn("event=request_expired_dropped stage={} payloadType={} correlationId={} overdueMs={}",
                stage, payloadType, correlationId, overdueMs);
        return new com.evento.transport.RequestExpiredException(
                "request " + correlationId + " (" + payloadType + ") not executed: the caller's deadline passed "
                        + overdueMs + " ms ago (stage " + stage + ")");
    }

    /** How many requests were dropped as expired at {@code stage} (see {@link #EXPIRED_STAGES}). */
    public long expiredDroppedCount(String stage) {
        var c = expiredDropped.get(stage);
        return c == null ? 0L : c.get();
    }

    // Hot-path counters exposing whether the zero-copy forward fired (good) or
    // fell back to encode-on-forward (e.g. the destination is an InMemoryTransport
    // in tests). Both are AtomicLong so they're safe to read from any thread.
    private final java.util.concurrent.atomic.AtomicLong forwardedRawCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong forwardedReencodedCount = new java.util.concurrent.atomic.AtomicLong();

    /**
     * Holds responses whose originator was disconnected at the time the handler
     * replied. Keyed by originator instanceId. On reconnect the buffered entries
     * are replayed to the new transport so in-flight futures can complete.
     * Entries expire after {@link #RECONNECT_BUFFER_TTL_MS} to bound memory.
     */
    private record PendingResponseEntry(Response response, Frame frame, long expiresAtMs) {}
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<PendingResponseEntry>> reconnectBuffer
            = new ConcurrentHashMap<>();
    private static final long RECONNECT_BUFFER_TTL_MS = 120_000L;

    // Background maintenance: prunes the forwarding table (relays whose originator died mid-call)
    // and expired reconnect-buffer entries (responses for originators that never came back).
    // Without this both grow unbounded under churn. Started in start(), stopped in stop().
    private static final long FORWARDING_TABLE_TTL_MS = 5 * 60_000L;
    private static final long MAINTENANCE_INTERVAL_MS = 30_000L;
    private volatile java.util.concurrent.ScheduledExecutorService maintenanceScheduler;

    /**
     * Whether a health probe (a Ping on a connection that has sent nothing else)
     * gets a Pong. Always true unless {@link #setProbeReadiness} says otherwise.
     */
    private volatile java.util.function.BooleanSupplier probeReadiness = () -> true;

    public BusLifecycle(TransportServer transportServer,
                        ConnectionRegistry connectionRegistry,
                        ClusterRegistry clusterRegistry,
                        CorrelationStore correlationStore,
                        ForwardingTable forwardingTable,
                        BusEventBus eventBus,
                        String serverInstanceId,
                        Set<String> serverCapabilities) {
        this(transportServer, connectionRegistry, clusterRegistry, correlationStore,
                forwardingTable, eventBus, serverInstanceId, serverCapabilities,
                new JacksonCborPayloadCodec(), TokenValidator.acceptAll());
    }

    public BusLifecycle(TransportServer transportServer,
                        ConnectionRegistry connectionRegistry,
                        ClusterRegistry clusterRegistry,
                        CorrelationStore correlationStore,
                        ForwardingTable forwardingTable,
                        BusEventBus eventBus,
                        String serverInstanceId,
                        Set<String> serverCapabilities,
                        PayloadCodec payloadCodec) {
        this(transportServer, connectionRegistry, clusterRegistry, correlationStore,
                forwardingTable, eventBus, serverInstanceId, serverCapabilities,
                payloadCodec, TokenValidator.acceptAll());
    }

    public BusLifecycle(TransportServer transportServer,
                        ConnectionRegistry connectionRegistry,
                        ClusterRegistry clusterRegistry,
                        CorrelationStore correlationStore,
                        ForwardingTable forwardingTable,
                        BusEventBus eventBus,
                        String serverInstanceId,
                        Set<String> serverCapabilities,
                        PayloadCodec payloadCodec,
                        TokenValidator tokenValidator) {
        this(transportServer, connectionRegistry, clusterRegistry, correlationStore,
                forwardingTable, eventBus, serverInstanceId, serverCapabilities,
                payloadCodec, tokenValidator, new ForwardingDedupCache());
    }

    public BusLifecycle(TransportServer transportServer,
                        ConnectionRegistry connectionRegistry,
                        ClusterRegistry clusterRegistry,
                        CorrelationStore correlationStore,
                        ForwardingTable forwardingTable,
                        BusEventBus eventBus,
                        String serverInstanceId,
                        Set<String> serverCapabilities,
                        PayloadCodec payloadCodec,
                        TokenValidator tokenValidator,
                        ForwardingDedupCache dedupCache) {
        this.transportServer = transportServer;
        this.connectionRegistry = connectionRegistry;
        this.clusterRegistry = clusterRegistry;
        this.correlationStore = correlationStore;
        this.forwardingTable = forwardingTable;
        this.eventBus = eventBus;
        this.payloadCodec = payloadCodec;
        this.tokenValidator = tokenValidator;
        this.dedupCache = dedupCache;
        this.serverInstanceId = serverInstanceId;
        this.handshakeHandler = new HandshakeHandler(
                serverInstanceId,
                serverCapabilities,
                (hello, info) -> validateHello(hello));
        this.router = new MessageRouter(
                this::onHello,
                this::onRequest,
                this::onResponse,
                this::onNotification);
        transportServer.onConnection(this::acceptConnection);
        transportServer.setPreSessionReadiness(() -> probeReadiness.getAsBoolean());
    }

    /**
     * What a bundle's fail-back probe is told (N16). A bundle that failed over
     * to another broker probes this one with a bare transport Ping, never a
     * Hello, and moves back once the Pings have been answered for its stability
     * window. The bus binds its port early — in the Spring server before the
     * application context has finished starting — so answering on a bound port
     * alone would call this broker healthy while its database or event store are
     * still coming up, and the bundles would move back only to fail. While
     * {@code ready} is false such Pings go unanswered and the probes time out.
     * Pings on sessions that have sent a Hello are always answered. Can be called
     * at any time; null restores "always ready".
     */
    public void setProbeReadiness(java.util.function.BooleanSupplier ready) {
        this.probeReadiness = ready == null ? () -> true : ready;
    }

    /**
     * The cluster name announced in every {@code Welcome} ({@code evento.cluster.name}).
     * Bundles configured with {@code expectedClusterName} refuse a broker that
     * announces another name, or none. Call before {@link #start(int)}.
     */
    public void setClusterName(String clusterName) {
        handshakeHandler.setClusterName(clusterName);
    }

    public int start(int port) {
        com.evento.common.utils.JdkRuntimeCheck.warning().ifPresent(log::warn);
        int bound = transportServer.start(port);
        var scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "evento-bus-maintenance");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::runMaintenance,
                MAINTENANCE_INTERVAL_MS, MAINTENANCE_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        this.maintenanceScheduler = scheduler;
        log.info("event=lifecycle_started port={}", bound);
        return bound;
    }

    public void stop(Duration deadline) {
        log.info("event=lifecycle_stopping deadline_ms={}", deadline.toMillis());
        var scheduler = this.maintenanceScheduler;
        if (scheduler != null) {
            scheduler.shutdownNow();
            this.maintenanceScheduler = null;
        }
        transportServer.stop();
        correlationStore.shutdown(deadline);
        connectionRegistry.closeAll("lifecycle_stop");
        log.info("event=lifecycle_stopped");
    }

    /**
     * Periodic, best-effort memory bound: drop forwarding-table relays older than
     * {@link #FORWARDING_TABLE_TTL_MS} (originator died mid-call / handler never replied) and
     * reconnect-buffer entries past their TTL (originator never reconnected). Swallows its own
     * exceptions so a transient failure never kills the scheduler.
     */
    private void runMaintenance() {
        try {
            long now = System.currentTimeMillis();
            forwardingTable.removeOlderThan(now - FORWARDING_TABLE_TTL_MS);
            pruneReconnectBuffer(now);
        } catch (Throwable t) {
            FatalErrors.escalateIfFatal(t);
            log.warn("event=bus_maintenance_failed", t);
        }
    }

    /**
     * Removes expired buffered responses. Empty queue shells are intentionally left in place:
     * the add path is {@code computeIfAbsent(id).add(entry)}, so removing an empty queue here
     * could race with a concurrent add and drop a just-buffered response. The shells are tiny
     * and bounded by the number of distinct originator instanceIds.
     */
    private void pruneReconnectBuffer(long now) {
        int removed = 0;
        for (var queue : reconnectBuffer.values()) {
            for (var it = queue.iterator(); it.hasNext(); ) {
                if (now > it.next().expiresAtMs()) {
                    it.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            log.info("event=reconnect_buffer_pruned removed={}", removed);
        }
    }

    public Set<NodeAddress> view() { return connectionRegistry.view(); }
    public Set<NodeAddress> availableView() { return connectionRegistry.availableView(); }
    public boolean isBundleAvailable(String bundleId) { return connectionRegistry.isAvailable(bundleId); }
    /** instanceId → zone, for every connected node that advertised one. */
    public java.util.Map<String, String> zones() { return clusterRegistry.zones(); }
    public void subscribe(Consumer<BusEvent> listener) { eventBus.subscribe(listener); }
    public int boundPort() { return transportServer.boundPort(); }

    /**
     * Send a server-initiated {@link Request} to a specific {@code destination}
     * node and return a future that completes when the matching {@link Response}
     * arrives (or fails on timeout / peer disconnect).
     *
     * <p>This is the primitive {@code BusFacade.forward(...)} sits on top of —
     * it lets the server originate traffic to a chosen bundle without going
     * through the routing table.
     *
     * @param destination the bundle node to send to
     * @param payloadType the wire {@code payloadType} string (typically
     *                    {@code ProtocolPayloadTypes.SERVER_ADMIN_REQUEST} for
     *                    admin RPCs)
     * @param payload     opaque CBOR bytes that the bundle will decode
     * @param timeout     how long to wait for the response before failing the
     *                    future
     */
    public java.util.concurrent.CompletableFuture<Response> forward(NodeAddress destination,
                                                                     String payloadType,
                                                                     byte[] payload,
                                                                     Duration timeout) {
        java.util.Objects.requireNonNull(destination, "destination");
        java.util.Objects.requireNonNull(payloadType, "payloadType");
        var destConnection = connectionRegistry.lookup(destination);
        if (destConnection.isEmpty()) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException("no connection for " + destination.instanceId()));
        }
        var correlationId = UUID.randomUUID();
        var request = new Request(correlationId,
                SERVER_BUNDLE_ID, serverInstanceId, "0",
                payloadType,
                payload == null ? new byte[0] : payload,
                timeout.toMillis(),
                System.currentTimeMillis());
        var future = correlationStore.submit(null, destination, correlationId,
                payloadType, timeout.toMillis());
        try {
            destConnection.get().transport().send(request);
        } catch (SendFailedException sfe) {
            // Roll the correlation we just registered so the future doesn't hang
            // waiting for a response that will never come.
            correlationStore.failMatching(c -> c.correlationId().equals(correlationId), sfe);
            return java.util.concurrent.CompletableFuture.failedFuture(sfe);
        }
        return future;
    }

    /**
     * Block the calling thread until at least one node for {@code bundleId} is
     * available (i.e. has sent {@code evento:enable}), or until {@code deadline}
     * elapses. Returns true if the bundle became available in time, false on
     * timeout.
     *
     * <p>Intended for boot-time waits where a controller needs a bundle online
     * before it can start serving traffic. Implementation is a busy poll on the
     * registry — fine because callers use it rarely, at startup only.
     */
    public boolean waitUntilAvailable(String bundleId, Duration deadline) {
        java.util.Objects.requireNonNull(bundleId, "bundleId");
        long endNanos = System.nanoTime() + deadline.toNanos();
        while (System.nanoTime() < endNanos) {
            if (connectionRegistry.isAvailable(bundleId)) return true;
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return connectionRegistry.isAvailable(bundleId);
    }

    /** How many forwards used the zero-copy {@code sendRaw} path (no codec hit). */
    public long forwardedRawCount() { return forwardedRawCount.get(); }

    /** How many forwards fell back to the typed {@link Transport#send} path (codec re-encoded). */
    public long forwardedReencodedCount() { return forwardedReencodedCount.get(); }

    /** Public hook for tests / non-TCP transports: feed an already-accepted Transport. */
    public void acceptConnection(Transport transport) {
        var session = new BundleSession(transport, UUID.randomUUID().toString());
        sessionsByTransport.put(transport, session);
        transport.onFrame(frame -> {
            session.markFrameReceived();
            router.route(frame, session);
        });
        transport.onStateChange((from, to) -> {
            if (to == ConnectionState.DISCONNECTED || to == ConnectionState.CLOSED) {
                onTransportDisconnected(session);
            }
        });
        // DEBUG: every fail-back probe (a bundle checking whether this broker is back,
        // every few seconds) is an accepted connection that only pings. A real session
        // is logged at INFO by its Hello.
        log.debug("event=session_accepted token={}", session.connectionToken());
    }

    // ---- inbound handlers ----

    private void onHello(Hello hello, Frame frame, BundleSession session) {
        if (session.address() != null) {
            log.warn("event=duplicate_hello session={}", session.address().instanceId());
            return;
        }
        // Validate first, and touch no shared state for a Hello that is refused. A rejected
        // Hello (bad token, wrong protocol) may carry the instanceId of a session that is live
        // here or on another server: registering it even speculatively published NodeJoined
        // then NodeLeft for that instance, whose presence claim and release then wiped the
        // live instance's catalog rows (G7), and overwrote then erased its zone.
        var verdict = handshakeHandler.validate(hello);
        if (verdict instanceof HandshakeOutcome.Rejected) {
            handshakeHandler.reply(hello, session.transport(), verdict)
                    .whenComplete((outcome, ex) -> session.transport().close());
            return;
        }
        // Bind + register synchronously, BEFORE sending Welcome. Otherwise the client
        // can receive Welcome and immediately fire follow-up notifications (enable,
        // register-handlers, even a Request) that arrive on the server before
        // session.address() is set, and get dropped as "before-handshake".
        long version = parseBundleVersion(hello.bundleVersion());
        var address = new NodeAddress(hello.bundleId(), version, hello.instanceId());
        session.bindAddress(address);
        session.transitionTo(BundleSession.Phase.REGISTERED);
        connectionRegistry.register(new Connection(
                address, session.transport(), session.connectionToken(), Instant.now()));
        // The zone rides in the Hello capabilities (no wire field of its own). It is
        // recorded on every Hello, so a reconnect that moved zones is picked up; recorded
        // after the registration and tagged with this session's token, so the teardown of
        // the session this one replaces can no longer erase it (see onTransportDisconnected).
        clusterRegistry.setZone(address, session.connectionToken(), HandshakeProtocol.zoneOf(hello.capabilities()));
        handshakeHandler.reply(hello, session.transport(), verdict).whenComplete((outcome, ex) -> {
            if (ex != null) {
                log.error("event=handshake_send_failed", ex);
                session.transport().close();
                return;
            }
            // Replay any responses buffered while this instance was disconnected.
            deliverPendingResponses(address, session.transport());
        });
    }

    private void onNotification(Notification notification, Frame frame, BundleSession session) {
        if (session.address() == null) {
            log.warn("event=notification_before_handshake type={}", notification.payloadType());
            return;
        }
        switch (notification.payloadType()) {
            case BundleRegistrationInfo.PAYLOAD_TYPE -> {
                var info = payloadCodec.decode(notification.payload(), BundleRegistrationInfo.class);
                // Replace (not just add): remove stale entries first so that a
                // reconnecting bundle with a changed handler set doesn't leave
                // old payload types dangling in the routing table.
                var evictedTypes = clusterRegistry.removeNode(session.address());
                evictedTypes.forEach(localHandlers::remove);
                clusterRegistry.registerHandlers(session.address(), info.handlerPayloadTypes());
                eventBus.publish(new BusEvent.BundleRegistered(session.address(), info, Instant.now()));
            }
            case NOTIFY_ENABLE -> {
                connectionRegistry.enable(session.address());
                session.transitionTo(BundleSession.Phase.READY);
            }
            case ProtocolNotifications.BUNDLE_DISCOVERY -> {
                if (session.address() == null) {
                    log.warn("event=discovery_before_handshake");
                    return;
                }
                var discovery = payloadCodec.decode(notification.payload(), BundleDiscoveryInfo.class);
                eventBus.publish(new BusEvent.BundleDiscovered(session.address(), discovery, Instant.now()));
            }
            case NOTIFY_DISABLE -> connectionRegistry.disable(session.address());
            default -> {
                // Application-level notification — surface it on the event bus so
                // server-side subscribers can pattern-match (e.g. the bundle-admin
                // notification listener that handles performance metrics +
                // consumer registration).
                eventBus.publish(new BusEvent.AdminNotification(
                        session.address(), notification.payloadType(),
                        notification.payload(), Instant.now()));
            }
        }
    }

    private void onRequest(Request request, Frame frame, BundleSession session) {
        if (session.address() == null) {
            replyWithError(session.transport(), request.correlationId(),
                    new IllegalStateException("handshake required before requests"));
            return;
        }
        // The caller's deadline, measured from when the frame reached this broker (before
        // it queued for a dispatch thread): a request nobody waits for any more is not
        // routed, so a saturated broker stops executing commands whose callers already
        // saw a timeout (and may be retrying them). No timeout = no deadline.
        long deadlineMs = request.deadlineFrom(frame == null ? monotonicMillis() : frame.receivedAtMonotonicMs());
        if (monotonicMillis() >= deadlineMs) {
            replyWithError(session.transport(), request.correlationId(),
                    expiredDrop(EXPIRED_STAGE_QUEUED, request.payloadType(), request.correlationId(), deadlineMs));
            return;
        }
        // Exactly-once at the broker: a duplicate of an already-answered request
        // gets the cached response; a duplicate of an in-flight request is dropped.
        var dedupOutcome = dedupCache.resolveOrClaim(request.correlationId());
        switch (dedupOutcome) {
            case ForwardingDedupCache.Outcome.Replay replay -> {
                log.info("event=request_dedup_replay correlationId={} payloadType={}",
                        request.correlationId(), request.payloadType());
                session.transport().send(replay.response());
                return;
            }
            case ForwardingDedupCache.Outcome.InFlight ignored -> {
                log.info("event=request_dedup_drop_inflight correlationId={} payloadType={}",
                        request.correlationId(), request.payloadType());
                return;
            }
            case ForwardingDedupCache.Outcome.Claimed ignored -> { /* fresh; route below */ }
        }

        var localHandler = localHandlers.get(request.payloadType());
        if (localHandler != null) {
            try {
                byte[] result = localHandler.handle(request.payload(), session.address(), deadlineMs);
                var response = Response.success(request.correlationId(), request.payloadType(), result);
                dedupCache.recordResponse(request.correlationId(), response);
                session.transport().send(response);
            } catch (com.evento.transport.RequestExpiredException expired) {
                // Already logged as event=request_expired_dropped where it was refused.
                dedupCache.invalidate(request.correlationId());
                replyWithError(session.transport(), request.correlationId(), expired);
            } catch (Throwable t) {
                FatalErrors.escalateIfFatal(t);
                log.warn("event=local_handler_threw payloadType={} correlationId={}",
                        request.payloadType(), request.correlationId(), t);
                dedupCache.invalidate(request.correlationId());
                replyWithError(session.transport(), request.correlationId(), t);
            }
            return;
        }

        // The originator is the bundle on this session; LOCALITY_PREFERRED uses its zone.
        var destination = clusterRegistry.pickFor(request.payloadType(), session.address());
        if (destination.isEmpty()) {
            dedupCache.invalidate(request.correlationId());  // don't cache routing-side failures
            replyWithError(session.transport(), request.correlationId(),
                    new IllegalStateException("no handler for " + request.payloadType()));
            return;
        }
        var destAddress = destination.get();
        var destConnection = connectionRegistry.lookup(destAddress);
        if (destConnection.isEmpty()) {
            dedupCache.invalidate(request.correlationId());
            replyWithError(session.transport(), request.correlationId(),
                    new IllegalStateException("handler vanished: " + destAddress.instanceId()));
            return;
        }
        forwardingTable.track(request.correlationId(), session.address(), destAddress, request.payloadType());
        // Zero-copy forward: the broker doesn't re-encode the Request — it writes
        // the exact bytes that arrived on the wire to the destination's channel.
        // Saves one CBOR pass + one byte[] allocation on every hop. Fall back to
        // send(Request) when the destination transport doesn't support sendRaw
        // (in-memory transports used by tests).
        forwardOrSend(destConnection.get().transport(), frame, request).exceptionally(t -> {
            forwardingTable.resolve(request.correlationId());
            dedupCache.invalidate(request.correlationId());
            replyWithError(session.transport(), request.correlationId(), t);
            return null;
        });
    }

    private void onResponse(Response response, Frame frame, BundleSession session) {
        if (correlationStore.complete(response)) {
            return;  // server-initiated request
        }
        // Cache the response so duplicate requests on the way get a replay.
        dedupCache.recordResponse(response.correlationId(), response);
        var entry = forwardingTable.resolve(response.correlationId());
        if (entry.isEmpty()) {
            log.debug("event=orphan_response correlationId={}", response.correlationId());
            return;
        }
        var originator = entry.get().originator();
        var originatorConn = connectionRegistry.lookup(originator);
        if (originatorConn.isEmpty()) {
            // Originator disconnected before the handler replied. Buffer the
            // response so it can be replayed when the originator reconnects.
            reconnectBuffer
                    .computeIfAbsent(originator.instanceId(), k -> new ConcurrentLinkedQueue<>())
                    .add(new PendingResponseEntry(response, frame,
                            System.currentTimeMillis() + RECONNECT_BUFFER_TTL_MS));
            log.info("event=response_buffered_for_reconnect correlationId={} originator={}",
                    response.correlationId(), originator.instanceId());
            return;
        }
        forwardOrSend(originatorConn.get().transport(), frame, response);
    }

    /**
     * Prefer the zero-copy {@link Transport#sendRaw} path if the destination
     * supports it and we have raw bytes from the inbound frame. Otherwise fall
     * back to the typed send (which re-encodes via the codec).
     */
    private java.util.concurrent.CompletableFuture<Void> forwardOrSend(Transport dest,
                                                                        Frame frame,
                                                                        com.evento.transport.message.Message fallback) {
        byte[] raw = frame == null ? null : frame.rawBytes();
        if (raw == null || raw.length == 0) {
            forwardedReencodedCount.incrementAndGet();
            return dest.send(fallback);
        }
        // Counted before the write: once the bytes are out the peer can answer, and a
        // reader of the counter (the zero-copy IT) could see the reply before the count.
        forwardedRawCount.incrementAndGet();
        try {
            return dest.sendRaw(raw);
        } catch (UnsupportedOperationException uoe) {
            forwardedRawCount.decrementAndGet();
            forwardedReencodedCount.incrementAndGet();
            return dest.send(fallback);
        }
    }

    private void onTransportDisconnected(BundleSession session) {
        sessionsByTransport.remove(session.transport());
        var address = session.address();
        if (address == null) {
            if (session.framesReceived()) {
                log.info("event=preregistered_disconnect token={}", session.connectionToken());
            } else {
                // Nothing but heartbeats (swallowed below the router): a health probe.
                log.debug("event=preregistered_disconnect token={} frames=none", session.connectionToken());
            }
            return;
        }
        session.transitionTo(BundleSession.Phase.CLOSED);
        var removedConn = connectionRegistry.unregister(address, session.connectionToken(), "transport_disconnected");
        if (removedConn.isEmpty()) {
            // Token mismatch: this session was already superseded by a newer connection
            // for the same NodeAddress. The new session owns the cluster-registry and
            // forwarding-table entries — do not touch them.
            log.info("event=disconnect_superseded_skip node={} token={}",
                    address.instanceId(), session.connectionToken());
            return;
        }
        // Remove the node from the routing table and evict any local handlers
        // (e.g. CommandBrokerHandler's aggregate/service command interceptors)
        // whose target set just became empty. Without this, stale localHandlers
        // entries keep "handling" requests and fail with "no available handler"
        // instead of the cleaner "no handler for" routing-miss path.
        var nowEmptyTypes = clusterRegistry.removeNode(address);
        nowEmptyTypes.forEach(localHandlers::remove);
        // Token-scoped: if a newer session of this instance has registered meanwhile (its
        // Hello ran on another event loop between the unregister above and this line), the
        // zone it recorded stays.
        clusterRegistry.forgetZone(address, session.connectionToken());

        // Only drain entries where the disconnecting peer was the *destination*
        // (handler). Surface a failure to each originator so its future resolves
        // immediately rather than timing out.
        //
        // Entries where the disconnecting peer is the *originator* are left in
        // the table intentionally: if the originator reconnects before the
        // handler replies, the response will find the new connection via
        // connectionRegistry.lookup(); if the handler replies while the
        // originator is still disconnected, onResponse() buffers the response
        // in reconnectBuffer for replay on reconnect.
        var disconnectCause = new IllegalStateException("peer disconnected: " + address.instanceId());
        for (var entry : forwardingTable.drainByDestination(address)) {
            connectionRegistry.lookup(entry.originator()).ifPresent(originatorConn -> {
                var err = Response.failure(entry.correlationId(), ResponseError.of(disconnectCause));
                try {
                    originatorConn.transport().send(err);
                } catch (SendFailedException sfe) {
                    log.warn("event=disconnect_notify_failed correlationId={} originator={}",
                            entry.correlationId(), entry.originator().instanceId(), sfe);
                }
            });
        }

        // Server-initiated correlations (no forwarding involved) also fail.
        correlationStore.failMatching(c -> c.to().equals(address), disconnectCause);
    }

    // ---- helpers ----

    private void deliverPendingResponses(NodeAddress address, Transport transport) {
        var queue = reconnectBuffer.remove(address.instanceId());
        if (queue == null || queue.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (var pending : queue) {
            if (now > pending.expiresAtMs()) {
                log.debug("event=buffered_response_expired correlationId={}",
                        pending.response().correlationId());
                continue;
            }
            log.info("event=buffered_response_delivered correlationId={} originator={}",
                    pending.response().correlationId(), address.instanceId());
            forwardOrSend(transport, pending.frame(), pending.response());
        }
    }

    private HandshakeOutcome validateHello(Hello hello) {
        if (hello.protocolVersion() != HandshakeProtocol.PROTOCOL_VERSION) {
            return new HandshakeOutcome.Rejected(
                    com.evento.transport.message.Reject.CODE_PROTOCOL_VERSION,
                    "unsupported protocol version " + hello.protocolVersion());
        }
        var verdict = tokenValidator.validate(hello.bundleId(), hello.instanceId(), hello.authToken());
        if (verdict instanceof TokenValidator.Decision.Reject reject) {
            log.warn("event=auth_rejected bundle={} instance={} reason={}",
                    hello.bundleId(), hello.instanceId(), reject.reason());
            return new HandshakeOutcome.Rejected(
                    com.evento.transport.message.Reject.CODE_AUTH_FAILED, reject.reason());
        }
        var probableAddress = new NodeAddress(hello.bundleId(),
                parseBundleVersion(hello.bundleVersion()), hello.instanceId());
        if (connectionRegistry.lookup(probableAddress).isPresent()) {
            // Allow supersede — the registry will handle the close + replace.
            log.info("event=hello_will_supersede instance={}", hello.instanceId());
        }
        return new HandshakeOutcome.Accepted(probableAddress,
                new HandshakeHandler.BundleVersionInfo(
                        hello.bundleId(), hello.instanceId(), hello.bundleVersion()));
    }

    private long parseBundleVersion(String version) {
        if (version == null) return 0L;
        try { return Long.parseLong(version); } catch (NumberFormatException e) { return version.hashCode(); }
    }

    private void replyWithError(Transport transport, UUID correlationId, Throwable cause) {
        var error = Response.failure(correlationId, ResponseError.of(cause));
        try {
            transport.send(error);
        } catch (SendFailedException sfe) {
            log.warn("event=error_reply_send_failed correlationId={}", correlationId, sfe);
        }
    }
}
