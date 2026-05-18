package com.evento.server.bus.v2.lifecycle;

import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.v2.correlation.CorrelationStore;
import com.evento.server.bus.v2.event.BusEvent;
import com.evento.server.bus.v2.event.BusEventBus;
import com.evento.server.bus.v2.handshake.BundleRegistrationInfo;
import com.evento.server.bus.v2.handshake.HandshakeHandler;
import com.evento.server.bus.v2.handshake.HandshakeHandler.HandshakeOutcome;
import com.evento.server.bus.v2.registry.ClusterRegistry;
import com.evento.server.bus.v2.registry.Connection;
import com.evento.server.bus.v2.registry.ConnectionRegistry;
import com.evento.server.bus.v2.router.BundleSession;
import com.evento.server.bus.v2.router.ForwardingTable;
import com.evento.server.bus.v2.router.MessageRouter;
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
 * consumers (view snapshots, sendKill, forward, isBundleAvailable) plus
 * {@link #subscribe(Consumer)} for cluster-event observers. Consumers will be
 * migrated off the v1 MessageBus to this in a follow-up commit.
 */
public final class BusLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BusLifecycle.class);

    // --- protocol-internal notification payload types ---
    public static final String NOTIFY_ENABLE = "evento:enable";
    public static final String NOTIFY_DISABLE = "evento:disable";
    public static final String NOTIFY_KILL = "evento:kill";

    private final TransportServer transportServer;
    private final ConnectionRegistry connectionRegistry;
    private final ClusterRegistry clusterRegistry;
    private final CorrelationStore correlationStore;
    private final ForwardingTable forwardingTable;
    private final BusEventBus eventBus;
    private final HandshakeHandler handshakeHandler;
    private final PayloadCodec payloadCodec;
    private final MessageRouter router;
    private final ConcurrentHashMap<Transport, BundleSession> sessionsByTransport = new ConcurrentHashMap<>();

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
                new JacksonCborPayloadCodec());
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
        this.transportServer = transportServer;
        this.connectionRegistry = connectionRegistry;
        this.clusterRegistry = clusterRegistry;
        this.correlationStore = correlationStore;
        this.forwardingTable = forwardingTable;
        this.eventBus = eventBus;
        this.payloadCodec = payloadCodec;
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

    /** Public hook for tests / non-TCP transports: feed an already-accepted Transport. */
    public void acceptConnection(Transport transport) {
        var session = new BundleSession(transport, UUID.randomUUID().toString());
        sessionsByTransport.put(transport, session);
        transport.onMessage(msg -> router.route(msg, session));
        transport.onStateChange((from, to) -> {
            if (to == ConnectionState.DISCONNECTED || to == ConnectionState.CLOSED) {
                onTransportDisconnected(session);
            }
        });
        log.info("event=session_accepted token={}", session.connectionToken());
    }

    // ---- inbound handlers ----

    private void onHello(Hello hello, BundleSession session) {
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

    private void onNotification(Notification notification, BundleSession session) {
        if (session.address() == null) {
            log.warn("event=notification_before_handshake type={}", notification.payloadType());
            return;
        }
        switch (notification.payloadType()) {
            case BundleRegistrationInfo.PAYLOAD_TYPE -> {
                var info = payloadCodec.decode(notification.payload(), BundleRegistrationInfo.class);
                clusterRegistry.registerHandlers(session.address(), info.handlerPayloadTypes());
            }
            case NOTIFY_ENABLE -> {
                connectionRegistry.enable(session.address());
                session.transitionTo(BundleSession.Phase.READY);
            }
            case NOTIFY_DISABLE -> connectionRegistry.disable(session.address());
            default -> log.debug("event=passthrough_notification type={} from={}",
                    notification.payloadType(), session.address().instanceId());
        }
    }

    private void onRequest(Request request, BundleSession session) {
        if (session.address() == null) {
            replyWithError(session.transport(), request.correlationId(),
                    new IllegalStateException("handshake required before requests"));
            return;
        }
        var destination = clusterRegistry.pick(request.payloadType());
        if (destination.isEmpty()) {
            replyWithError(session.transport(), request.correlationId(),
                    new IllegalStateException("no handler for " + request.payloadType()));
            return;
        }
        var destAddress = destination.get();
        var destConnection = connectionRegistry.lookup(destAddress);
        if (destConnection.isEmpty()) {
            replyWithError(session.transport(), request.correlationId(),
                    new IllegalStateException("handler vanished: " + destAddress.instanceId()));
            return;
        }
        forwardingTable.track(request.correlationId(), session.address(), destAddress, request.payloadType());
        destConnection.get().transport().send(request).exceptionally(t -> {
            forwardingTable.resolve(request.correlationId());
            replyWithError(session.transport(), request.correlationId(), t);
            return null;
        });
    }

    private void onResponse(Response response, BundleSession session) {
        if (correlationStore.complete(response)) {
            return;  // server-initiated request
        }
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
        originatorConn.get().transport().send(response);
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
