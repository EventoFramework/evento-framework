package com.evento.server.bus.v2.lifecycle;

import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.v2.correlation.CorrelationStore;
import com.evento.server.bus.v2.correlation.ForwardingDedupCache;
import com.evento.server.bus.v2.event.BusEvent;
import com.evento.server.bus.v2.event.BusEventBus;
import com.evento.transport.protocol.BundleRegistrationInfo;
import com.evento.server.bus.v2.handshake.HandshakeHandler;
import com.evento.server.bus.v2.handshake.HandshakeHandler.HandshakeOutcome;
import com.evento.server.bus.v2.registry.ClusterRegistry;
import com.evento.server.bus.v2.registry.Connection;
import com.evento.server.bus.v2.registry.ConnectionRegistry;
import com.evento.server.bus.v2.router.BundleSession;
import com.evento.server.bus.v2.router.ForwardingTable;
import com.evento.server.bus.v2.router.MessageRouter;
import com.evento.server.bus.v2.security.TokenValidator;
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

    private final ConcurrentHashMap<String, LocalRequestHandler> localHandlers = new ConcurrentHashMap<>();

    public void registerLocalHandler(String payloadType, LocalRequestHandler handler) {
        localHandlers.put(payloadType, handler);
    }

    // Hot-path counters exposing whether the zero-copy forward fired (good) or
    // fell back to encode-on-forward (e.g. the destination is an InMemoryTransport
    // in tests). Both are AtomicLong so they're safe to read from any thread.
    private final java.util.concurrent.atomic.AtomicLong forwardedRawCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong forwardedReencodedCount = new java.util.concurrent.atomic.AtomicLong();

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
    }

    public int start(int port) {
        int bound = transportServer.start(port);
        log.info("event=lifecycle_started port={}", bound);
        return bound;
    }

    public void stop(Duration deadline) {
        log.info("event=lifecycle_stopping deadline_ms={}", deadline.toMillis());
        transportServer.stop();
        correlationStore.shutdown(deadline);
        connectionRegistry.closeAll("lifecycle_stop");
        log.info("event=lifecycle_stopped");
    }

    public Set<NodeAddress> view() { return connectionRegistry.view(); }
    public Set<NodeAddress> availableView() { return connectionRegistry.availableView(); }
    public boolean isBundleAvailable(String bundleId) { return connectionRegistry.isAvailable(bundleId); }
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
        transport.onFrame(frame -> router.route(frame, session));
        transport.onStateChange((from, to) -> {
            if (to == ConnectionState.DISCONNECTED || to == ConnectionState.CLOSED) {
                onTransportDisconnected(session);
            }
        });
        log.info("event=session_accepted token={}", session.connectionToken());
    }

    // ---- inbound handlers ----

    private void onHello(Hello hello, Frame frame, BundleSession session) {
        if (session.address() != null) {
            log.warn("event=duplicate_hello session={}", session.address().instanceId());
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
        handshakeHandler.handle(hello, session.transport()).whenComplete((outcome, ex) -> {
            if (ex != null) {
                log.error("event=handshake_send_failed", ex);
                session.transport().close();
                return;
            }
            if (outcome instanceof HandshakeOutcome.Rejected) {
                // Roll back the speculative registration.
                connectionRegistry.unregister(address, session.connectionToken(), "handshake_rejected");
                session.transport().close();
            }
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
                clusterRegistry.registerHandlers(session.address(), info.handlerPayloadTypes());
                eventBus.publish(new BusEvent.BundleRegistered(session.address(), info, Instant.now()));
            }
            case NOTIFY_ENABLE -> {
                connectionRegistry.enable(session.address());
                session.transitionTo(BundleSession.Phase.READY);
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
                byte[] result = localHandler.handle(request.payload());
                var response = Response.success(request.correlationId(), request.payloadType(), result);
                dedupCache.recordResponse(request.correlationId(), response);
                session.transport().send(response);
            } catch (Throwable t) {
                log.warn("event=local_handler_threw payloadType={} correlationId={}",
                        request.payloadType(), request.correlationId(), t);
                dedupCache.invalidate(request.correlationId());
                replyWithError(session.transport(), request.correlationId(), t);
            }
            return;
        }

        var destination = clusterRegistry.pick(request.payloadType());
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
            log.warn("event=response_originator_gone correlationId={} originator={}",
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
        try {
            var fut = dest.sendRaw(raw);
            forwardedRawCount.incrementAndGet();
            return fut;
        } catch (UnsupportedOperationException uoe) {
            forwardedReencodedCount.incrementAndGet();
            return dest.send(fallback);
        }
    }

    private void onTransportDisconnected(BundleSession session) {
        sessionsByTransport.remove(session.transport());
        var address = session.address();
        if (address == null) {
            log.info("event=preregistered_disconnect token={}", session.connectionToken());
            return;
        }
        session.transitionTo(BundleSession.Phase.CLOSED);
        connectionRegistry.unregister(address, session.connectionToken(), "transport_disconnected");
        clusterRegistry.removeNode(address);

        // For every in-flight forwarded request that involved this peer, drain the
        // entry and surface a failure to the surviving counterparty so its future
        // doesn't hang forever. If the disconnecting peer was the *destination*
        // (handler), we know the originator is still waiting; if it was the
        // originator, the destination's eventual response (if any) will simply be
        // dropped by `onResponse` because there's no forwarding entry left.
        var drained = forwardingTable.drainInvolving(address);
        var disconnectCause = new IllegalStateException("peer disconnected: " + address.instanceId());
        for (var entry : drained) {
            if (!entry.destination().equals(address)) continue;  // originator-only — nothing to surface
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
