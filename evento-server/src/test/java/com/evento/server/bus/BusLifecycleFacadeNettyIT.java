package com.evento.server.bus;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.server.bus.BusFacade;
import com.evento.server.bus.NodeAddress;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.event.BusEvent;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.JacksonCborCodec;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.codec.PayloadCodec;
import com.evento.transport.message.Hello;
import com.evento.transport.message.Message;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import com.evento.transport.message.Welcome;
import com.evento.transport.netty.NettyClientTransport;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.protocol.BundleDiscoveryInfo;
import com.evento.transport.protocol.BundleRegistrationInfo;
import com.evento.transport.protocol.ProtocolPayloadTypes;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end exercise of the {@link BusLifecycleFacade} over real Netty TCP.
 * Proves the v2 facade satisfies the surface the dashboard / discovery /
 * consumer services rely on: view snapshots, kill notifications, the
 * {@code BusEvent} stream, and especially the CBOR-encoded
 * {@link EventoRequest} round-trip via {@code SERVER_ADMIN_REQUEST}.
 *
 * <p>The fake bundle in this test plays the role that the 3.2 bundle migration
 * will fill: a v2 client that recognises the {@code evento:server-admin-request}
 * payloadType, decodes the inner {@link EventoRequest}, and replies with an
 * encoded {@link EventoResponse}.
 */
class BusLifecycleFacadeNettyIT {

    /** Reply body — must implement Serializable to flow through the polymorphic codec. */
    public static final class EchoBody implements Serializable {
        public String echoed;
        public long replyTimestamp;
        public EchoBody() {}
        public EchoBody(String echoed, long ts) { this.echoed = echoed; this.replyTimestamp = ts; }
    }

    /** Request body — must implement Serializable to flow through the polymorphic codec. */
    public static final class PingBody implements Serializable {
        public String greeting;
        public PingBody() {}
        public PingBody(String greeting) { this.greeting = greeting; }
    }

    private NettyTransportConfig serverConfig;
    private NettyTransportConfig clientConfig;
    private BusLifecycle lifecycle;
    private BusFacade facade;
    private PayloadCodec payloadCodec;
    private AdminPayloadCodec adminCodec;
    private int port;

    @BeforeEach
    void setUp() {
        serverConfig = nettyConfig();
        clientConfig = nettyConfig();
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofMillis(100));
        var forwarding = new ForwardingTable();
        payloadCodec = new JacksonCborPayloadCodec();
        adminCodec = new AdminPayloadCodec();
        var server = new NettyServerTransport(serverConfig);
        lifecycle = new BusLifecycle(server, connections, cluster, correlations, forwarding,
                eventBus, "facade-it-server", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                payloadCodec);
        port = lifecycle.start(0);
        facade = new BusLifecycleFacade(lifecycle, adminCodec, Duration.ofSeconds(5));
        assertThat(port).isGreaterThan(0);
    }

    private NettyTransportConfig nettyConfig() {
        return new NettyTransportConfig(
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(5),
                16 * 1024 * 1024, 64 * 1024, 32 * 1024,
                new ExponentialBackoffWithJitter(), new JacksonCborCodec(),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(500));
    }

    /**
     * Fake v2 bundle that recognises the SERVER_ADMIN_REQUEST payloadType,
     * decodes the inner EventoRequest via {@link AdminPayloadCodec}, and lets
     * the test register a body-typed handler that produces an EventoResponse.
     */
    final class AdminAwareBundle implements AutoCloseable {
        final NettyClientTransport client;
        final String bundleId;
        final String instanceId;
        final ConcurrentHashMap<UUID, CompletableFuture<Welcome>> hellos = new ConcurrentHashMap<>();
        final List<Notification> notifications = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile java.util.function.Function<EventoRequest, Serializable> adminHandler;

        AdminAwareBundle(String bundleId, String instanceId) {
            this.bundleId = bundleId;
            this.instanceId = instanceId;
            this.client = new NettyClientTransport(bundleId, "127.0.0.1", port, clientConfig);
            client.onMessage(this::dispatch);
            client.connect().join();
        }

        private void dispatch(Message msg) {
            switch (msg) {
                case Welcome w -> {
                    var f = hellos.remove(w.correlationId());
                    if (f != null) f.complete(w);
                }
                case Notification n -> notifications.add(n);
                case Request req -> handleRequest(req);
                default -> { /* ignore */ }
            }
        }

        private void handleRequest(Request req) {
            if (!ProtocolPayloadTypes.SERVER_ADMIN_REQUEST.equals(req.payloadType())) return;
            var inner = adminCodec.decodeRequest(req.payload());
            var handler = adminHandler;
            if (handler == null) return;
            var replyBody = handler.apply(inner);
            var reply = new EventoResponse();
            reply.setCorrelationId(inner.getCorrelationId());
            reply.setBody(replyBody);
            reply.setTimestamp(System.currentTimeMillis());
            var encoded = adminCodec.encodeResponse(reply);
            client.send(Response.success(req.correlationId(),
                    ProtocolPayloadTypes.SERVER_ADMIN_REQUEST, encoded));
        }

        CompletableFuture<Welcome> sayHello() {
            var corr = UUID.randomUUID();
            var f = new CompletableFuture<Welcome>();
            hellos.put(corr, f);
            client.send(new Hello(corr, HandshakeProtocol.PROTOCOL_VERSION,
                    bundleId, instanceId, "100", Set.of(), null, System.currentTimeMillis()));
            return f;
        }

        void registerRich(List<RegisteredHandler> handlers, Map<String, com.evento.transport.protocol.PayloadDiscoveryInfo> payloadInfo) {
            var payloadTypes = handlers.stream().map(RegisteredHandler::getHandledPayload).toList();
            // Step 1: lean registration
            var info = new BundleRegistrationInfo(100L, payloadTypes);
            client.send(new Notification(UUID.randomUUID(),
                    BundleRegistrationInfo.PAYLOAD_TYPE,
                    payloadCodec.encode(info), System.currentTimeMillis())).join();
            // Step 2: enable
            client.send(new Notification(UUID.randomUUID(),
                    BusLifecycle.NOTIFY_ENABLE, new byte[0], System.currentTimeMillis())).join();
            // Step 3: rich discovery
            var discovery = new BundleDiscoveryInfo(100L, "", "", "", "L", handlers, payloadInfo);
            client.send(new Notification(UUID.randomUUID(),
                    BundleDiscoveryInfo.PAYLOAD_TYPE,
                    payloadCodec.encode(discovery), System.currentTimeMillis())).join();
        }

        @Override public void close() { client.close(); }
    }

    @Test
    void facadeExposesViewAndAvailableView() throws Exception {
        try (var bundle = new AdminAwareBundle("bundle-A", "inst-A1")) {
            bundle.sayHello().get(3, TimeUnit.SECONDS);
            bundle.registerRich(List.of(), Map.of());
            await().atMost(3, TimeUnit.SECONDS).until(() -> facade.currentAvailableView().size() == 1);

            assertThat(facade.currentView()).hasSize(1);
            assertThat(facade.isBundleAvailable("bundle-A")).isTrue();
            assertThat(facade.isBundleAvailable("bundle-Z")).isFalse();
        }
    }

    @Test
    void facadeSubscribeReceivesBundleDiscoveredWithRichData() throws Exception {
        var seen = new ArrayList<BusEvent>();
        facade.subscribe(seen::add);

        try (var bundle = new AdminAwareBundle("bundle-X", "inst-X1")) {
            bundle.sayHello().get(3, TimeUnit.SECONDS);
            var handler = new RegisteredHandler(
                    ComponentType.Service, "DemoService", HandlerType.CommandHandler,
                    PayloadType.Command, "com.DemoCommand",
                    "com.DemoEvent", false, "demoId");
            bundle.registerRich(List.of(handler),
                    Map.of("com.DemoCommand", new com.evento.transport.protocol.PayloadDiscoveryInfo(
                            "{\"type\":\"object\"}", "demo-domain", "", "", "", 0)));

            await().atMost(3, TimeUnit.SECONDS).until(() ->
                    seen.stream().anyMatch(e -> e instanceof BusEvent.BundleDiscovered));
            var discovered = (BusEvent.BundleDiscovered) seen.stream()
                    .filter(e -> e instanceof BusEvent.BundleDiscovered).findFirst().orElseThrow();
            assertThat(discovered.node().bundleId()).isEqualTo("bundle-X");
            assertThat(discovered.discovery().handlers()).hasSize(1);
            assertThat(discovered.discovery().handlers().getFirst().getComponentName())
                    .isEqualTo("DemoService");
            assertThat(discovered.discovery().payloadInfo()).containsKey("com.DemoCommand");
        }
    }

    @Test
    void facadeForwardRoundTripsEventoRequestThroughAdminPayloadType() throws Exception {
        try (var bundle = new AdminAwareBundle("bundle-A", "inst-A1")) {
            bundle.sayHello().get(3, TimeUnit.SECONDS);
            bundle.registerRich(List.of(), Map.of());
            await().atMost(3, TimeUnit.SECONDS).until(() -> facade.currentAvailableView().size() == 1);
            NodeAddress target = facade.currentView().iterator().next();

            // Bundle's admin handler echoes the PingBody greeting.
            bundle.adminHandler = req -> {
                var ping = (PingBody) req.getBody();
                return new EchoBody("echo:" + ping.greeting, System.currentTimeMillis());
            };

            var req = new EventoRequest();
            req.setCorrelationId(UUID.randomUUID().toString());
            req.setTimestamp(System.currentTimeMillis());
            req.setSourceBundleId("evento-server");
            req.setSourceInstanceId("facade-it-server");
            req.setSourceBundleVersion(0);
            req.setBody(new PingBody("hi"));

            var responseFuture = new CompletableFuture<EventoResponse>();
            facade.forward(target, req, responseFuture::complete);

            var response = responseFuture.get(3, TimeUnit.SECONDS);
            assertThat(response.getBody()).isInstanceOf(EchoBody.class);
            assertThat(((EchoBody) response.getBody()).echoed).isEqualTo("echo:hi");
        }
    }

    @Test
    void facadeForwardToUnknownDestinationDeliversErrorResponse() throws Exception {
        var ghost = new NodeAddress("ghost-bundle", 1L, "ghost-instance");
        var responseFuture = new CompletableFuture<EventoResponse>();
        var req = new EventoRequest();
        req.setCorrelationId(UUID.randomUUID().toString());
        req.setTimestamp(System.currentTimeMillis());
        req.setSourceBundleId("evento-server");
        req.setSourceInstanceId("facade-it-server");
        req.setBody(new PingBody("hi"));

        facade.forward(ghost, req, responseFuture::complete);

        var response = responseFuture.get(3, TimeUnit.SECONDS);
        assertThat(response.getBody())
                .isInstanceOf(com.evento.common.modeling.exceptions.ExceptionWrapper.class);
        var wrapper = (com.evento.common.modeling.exceptions.ExceptionWrapper) response.getBody();
        assertThat(wrapper.getMessage()).contains("ghost-instance");
    }
}
