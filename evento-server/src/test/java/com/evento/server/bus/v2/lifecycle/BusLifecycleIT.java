package com.evento.server.bus.v2.lifecycle;

import com.evento.server.bus.v2.correlation.CorrelationStore;
import com.evento.server.bus.v2.event.BusEvent;
import com.evento.server.bus.v2.event.BusEventBus;
import com.evento.server.bus.v2.handshake.BundleRegistrationInfo;
import com.evento.server.bus.v2.registry.ClusterRegistry;
import com.evento.server.bus.v2.registry.ConnectionRegistry;
import com.evento.server.bus.v2.router.ForwardingTable;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.Transport;
import com.evento.transport.TransportServer;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.codec.PayloadCodec;
import com.evento.transport.inmemory.InMemoryTransport;
import com.evento.transport.message.Hello;
import com.evento.transport.message.Message;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import com.evento.transport.message.Welcome;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end exercise of {@link BusLifecycle}: two simulated bundles connect
 * via paired {@link InMemoryTransport}s, complete the v2 handshake, register
 * their handlers, and exchange a routed Request/Response through the server.
 *
 * <p>No real TCP, no Netty — exercises only the routing & registry slice.
 * Network-level concerns are covered by {@code NettyTransportIT} in
 * {@code evento-transport-netty}.
 */
class BusLifecycleIT {

    /** Mock {@link TransportServer} that lets the test inject transports directly. */
    static final class InjectableServer implements TransportServer {
        private Consumer<Transport> handler;
        private int boundPort = -1;
        @Override public int start(int port) { this.boundPort = port; return port; }
        @Override public int boundPort() { return boundPort; }
        @Override public void onConnection(Consumer<Transport> h) { this.handler = h; }
        @Override public void stop() {}
        void inject(Transport t) { handler.accept(t); }
    }

    /** A test payload representing a domain command. */
    record CreateDemoCommand(String demoId, String name, long value) {
        @JsonCreator
        CreateDemoCommand(
                @JsonProperty("demoId") String demoId,
                @JsonProperty("name") String name,
                @JsonProperty("value") long value
        ) {
            this.demoId = demoId;
            this.name = name;
            this.value = value;
        }
    }

    /** A test payload representing a domain event. */
    record DemoCreatedEvent(String demoId, String name, long value, long createdAt) {
        @JsonCreator
        DemoCreatedEvent(
                @JsonProperty("demoId") String demoId,
                @JsonProperty("name") String name,
                @JsonProperty("value") long value,
                @JsonProperty("createdAt") long createdAt
        ) {
            this.demoId = demoId;
            this.name = name;
            this.value = value;
            this.createdAt = createdAt;
        }
    }

    private InjectableServer transportServer;
    private BusLifecycle lifecycle;
    private PayloadCodec payloadCodec;
    private BusEventBus eventBus;
    private List<BusEvent> seenEvents;

    @BeforeEach
    void setUp() {
        transportServer = new InjectableServer();
        eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofMillis(50));
        var forwarding = new ForwardingTable();
        payloadCodec = new JacksonCborPayloadCodec();
        lifecycle = new BusLifecycle(transportServer, connections, cluster, correlations,
                forwarding, eventBus, "server-1", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                payloadCodec);
        lifecycle.start(0);
        seenEvents = new ArrayList<>();
        eventBus.subscribe(seenEvents::add);
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(200));
    }

    /**
     * Test client that drives a bundle conversation: handshake, register
     * handlers, enable, send requests, handle inbound requests, capture
     * responses by correlation id.
     */
    static final class MockBundle {
        final InMemoryTransport clientSide;
        final InMemoryTransport serverSide;
        final String bundleId;
        final String instanceId;
        final ConcurrentHashMap<UUID, CompletableFuture<Response>> pending = new ConcurrentHashMap<>();
        final ConcurrentHashMap<UUID, CompletableFuture<Welcome>> pendingHellos = new ConcurrentHashMap<>();
        Consumer<Request> requestHandler;

        MockBundle(String bundleId, String instanceId, BusLifecycle lifecycle, InjectableServer server) {
            this.bundleId = bundleId;
            this.instanceId = instanceId;
            var pair = InMemoryTransport.pair(bundleId + "-client", bundleId + "-server");
            this.clientSide = pair.client();
            this.serverSide = pair.server();
            clientSide.onMessage(this::dispatchInbound);
            clientSide.connect().join();
            serverSide.connect().join();
            server.inject(serverSide);
        }

        private void dispatchInbound(Message msg) {
            switch (msg) {
                case Welcome w -> {
                    var f = pendingHellos.remove(w.correlationId());
                    if (f != null) f.complete(w);
                }
                case Response r -> {
                    var f = pending.remove(r.correlationId());
                    if (f != null) f.complete(r);
                }
                case Request req -> {
                    if (requestHandler != null) requestHandler.accept(req);
                }
                default -> {}
            }
        }

        CompletableFuture<Welcome> sayHello() {
            var corr = UUID.randomUUID();
            var future = new CompletableFuture<Welcome>();
            pendingHellos.put(corr, future);
            var hello = new Hello(corr, HandshakeProtocol.PROTOCOL_VERSION,
                    bundleId, instanceId, "100", Set.of(), System.currentTimeMillis());
            clientSide.send(hello);
            return future;
        }

        void registerHandlers(PayloadCodec codec, List<String> payloadTypes) {
            var info = new BundleRegistrationInfo(100L, payloadTypes, Map.of());
            clientSide.send(new Notification(UUID.randomUUID(),
                    BundleRegistrationInfo.PAYLOAD_TYPE,
                    codec.encode(info), System.currentTimeMillis()));
        }

        void enable() {
            clientSide.send(new Notification(UUID.randomUUID(),
                    BusLifecycle.NOTIFY_ENABLE, new byte[0], System.currentTimeMillis()));
        }

        CompletableFuture<Response> sendRequest(String payloadType, byte[] payload) {
            var corr = UUID.randomUUID();
            var future = new CompletableFuture<Response>();
            pending.put(corr, future);
            clientSide.send(new Request(corr, bundleId, instanceId, "100",
                    payloadType, payload, 5_000L, System.currentTimeMillis()));
            return future;
        }

        void replyToRequest(Request req, byte[] body, String payloadType) {
            clientSide.send(Response.ok(req.correlationId(), payloadType, body));
        }
    }

    private void completeHandshake(MockBundle bundle) {
        var welcome = bundle.sayHello().orTimeout(2, TimeUnit.SECONDS).join();
        assertThat(welcome.protocolVersion()).isEqualTo(HandshakeProtocol.PROTOCOL_VERSION);
        assertThat(welcome.serverInstanceId()).isEqualTo("server-1");
    }

    @Test
    void handshakeRegistersTheBundleInTheConnectionRegistry() {
        var bundle = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        completeHandshake(bundle);

        await().atMost(2, TimeUnit.SECONDS).until(() -> !lifecycle.view().isEmpty());
        assertThat(lifecycle.view()).hasSize(1);
        assertThat(lifecycle.view().iterator().next().bundleId()).isEqualTo("bundle-A");
        assertThat(seenEvents.stream().anyMatch(e -> e instanceof BusEvent.NodeJoined)).isTrue();
    }

    @Test
    void registrationAndEnableMakeBundleAvailable() {
        var bundle = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        completeHandshake(bundle);
        bundle.registerHandlers(payloadCodec, List.of("com.CreateDemo"));
        bundle.enable();

        await().atMost(2, TimeUnit.SECONDS).until(() -> lifecycle.isBundleAvailable("bundle-A"));
        assertThat(lifecycle.availableView()).hasSize(1);
    }

    @Test
    void routedRequestReachesHandlerBundleAndResponseReturns() throws Exception {
        // bundle A: caller. bundle B: handler for "com.CreateDemo".
        var bundleA = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        var bundleB = new MockBundle("bundle-B", "inst-B1", lifecycle, transportServer);

        completeHandshake(bundleA);
        completeHandshake(bundleB);

        bundleB.registerHandlers(payloadCodec, List.of("com.CreateDemo"));
        bundleB.enable();
        bundleA.enable();

        await().atMost(2, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

        bundleB.requestHandler = req -> {
            var cmd = payloadCodec.decode(req.payload(), CreateDemoCommand.class);
            var evt = new DemoCreatedEvent(cmd.demoId(), cmd.name(), cmd.value(), 1_700_000_000L);
            bundleB.replyToRequest(req, payloadCodec.encode(evt), DemoCreatedEvent.class.getName());
        };

        var cmd = new CreateDemoCommand("d-1", "Alpha", 42L);
        var responseFuture = bundleA.sendRequest("com.CreateDemo", payloadCodec.encode(cmd));
        Response response = responseFuture.get(3, TimeUnit.SECONDS);

        assertThat(response.isError()).isFalse();
        assertThat(response.payloadType()).isEqualTo(DemoCreatedEvent.class.getName());
        var decoded = payloadCodec.decode(response.payload(), DemoCreatedEvent.class);
        assertThat(decoded.demoId()).isEqualTo("d-1");
        assertThat(decoded.value()).isEqualTo(42L);
    }

    @Test
    void requestForUnknownPayloadTypeRepliesWithError() throws Exception {
        var bundleA = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        completeHandshake(bundleA);
        bundleA.enable();
        await().atMost(2, TimeUnit.SECONDS).until(() -> lifecycle.isBundleAvailable("bundle-A"));

        var future = bundleA.sendRequest("com.NobodyHandlesThis", new byte[0]);
        Response response = future.get(2, TimeUnit.SECONDS);

        assertThat(response.isError()).isTrue();
        assertThat(response.error().message()).contains("no handler for com.NobodyHandlesThis");
    }

    @Test
    void requestBeforeHandshakeIsRejected() throws Exception {
        var pair = InMemoryTransport.pair("rogue-client", "rogue-server");
        pair.client().connect().join();
        pair.server().connect().join();
        transportServer.inject(pair.server());

        // Bundle skips Hello and goes straight to Request.
        var corr = UUID.randomUUID();
        var future = new CompletableFuture<Response>();
        pair.client().onMessage(msg -> {
            if (msg instanceof Response r && r.correlationId().equals(corr)) future.complete(r);
        });
        pair.client().send(new Request(corr, "rogue", "rogue-i", "1", "anything",
                new byte[0], 1_000L, System.currentTimeMillis())).join();

        Response response = future.get(2, TimeUnit.SECONDS);
        assertThat(response.isError()).isTrue();
        assertThat(response.error().message()).contains("handshake required");
    }

    @Test
    void disconnectRemovesNodeFromAllRegistries() {
        var bundleA = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        completeHandshake(bundleA);
        bundleA.registerHandlers(payloadCodec, List.of("com.X"));
        bundleA.enable();
        await().atMost(2, TimeUnit.SECONDS).until(() -> lifecycle.isBundleAvailable("bundle-A"));

        bundleA.clientSide.close();

        await().atMost(2, TimeUnit.SECONDS).until(() -> lifecycle.view().isEmpty());
        assertThat(lifecycle.availableView()).isEmpty();
        assertThat(seenEvents.stream().anyMatch(e -> e instanceof BusEvent.NodeLeft)).isTrue();
    }

    @Test
    void supersededReconnectKeepsViewSizeAtOne() {
        var first = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        completeHandshake(first);
        await().atMost(2, TimeUnit.SECONDS).until(() -> !lifecycle.view().isEmpty());

        var second = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        completeHandshake(second);

        await().atMost(2, TimeUnit.SECONDS).until(() ->
                lifecycle.view().size() == 1 &&
                seenEvents.stream().anyMatch(e ->
                        e instanceof BusEvent.NodeLeft l && "superseded".equals(l.reason())));
    }
}
