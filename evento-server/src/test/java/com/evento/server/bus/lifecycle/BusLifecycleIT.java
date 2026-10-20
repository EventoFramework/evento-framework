package com.evento.server.bus.lifecycle;

import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.event.BusEvent;
import com.evento.server.bus.event.BusEventBus;
import com.evento.transport.protocol.BundleDiscoveryInfo;
import com.evento.transport.protocol.BundleRegistrationInfo;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private ClusterRegistry cluster;
    private List<BusEvent> seenEvents;

    @BeforeEach
    void setUp() {
        transportServer = new InjectableServer();
        eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofMillis(50));
        var forwarding = new ForwardingTable();
        payloadCodec = new JacksonCborPayloadCodec();
        lifecycle = new BusLifecycle(transportServer, connections, cluster, correlations,
                forwarding, eventBus, "server-1", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                payloadCodec);
        lifecycle.start(0);
        seenEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
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
        Consumer<Notification> notificationHandler;

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
                case Notification n -> {
                    if (notificationHandler != null) notificationHandler.accept(n);
                }
                default -> {}
            }
        }

        CompletableFuture<Welcome> sayHello() {
            return sayHello(HandshakeProtocol.PROTOCOL_VERSION, Set.of());
        }

        CompletableFuture<Welcome> sayHello(int protocolVersion, Set<String> capabilities) {
            var corr = UUID.randomUUID();
            var future = new CompletableFuture<Welcome>();
            pendingHellos.put(corr, future);
            var hello = new Hello(corr, (byte) protocolVersion,
                    bundleId, instanceId, "100", capabilities, null, System.currentTimeMillis());
            clientSide.send(hello);
            return future;
        }

        void registerHandlers(PayloadCodec codec, List<String> payloadTypes) {
            var info = BundleRegistrationInfo.lean(100L, payloadTypes);
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
            clientSide.send(Response.success(req.correlationId(), payloadType, body));
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
    void aRejectedHelloReusingALiveInstanceIdLeavesThatInstanceAlone() throws Exception {
        // The live instance, in zone EU.
        var live = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        live.sayHello(HandshakeProtocol.PROTOCOL_VERSION, Set.of(HandshakeProtocol.zoneCapability("EU")))
                .orTimeout(2, TimeUnit.SECONDS).join();
        await().atMost(2, TimeUnit.SECONDS).until(() -> lifecycle.view().size() == 1);
        assertThat(lifecycle.zones()).containsEntry("inst-A1", "EU");
        seenEvents.clear();

        // A Hello refused by the validator (here: protocol version) carrying the same
        // instanceId, e.g. a misconfigured duplicate. It used to be registered speculatively:
        // NodeJoined + NodeLeft for the live instance (presence claim, then catalog cleanup),
        // and its zone overwritten then erased.
        var impostor = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        impostor.sayHello(HandshakeProtocol.PROTOCOL_VERSION + 9, Set.of(HandshakeProtocol.zoneCapability("AM")));
        await().atMost(2, TimeUnit.SECONDS).until(() ->
                impostor.serverSide.state() == com.evento.transport.state.ConnectionState.CLOSED
                        || impostor.serverSide.state() == com.evento.transport.state.ConnectionState.DISCONNECTED);
        Thread.sleep(100);

        assertThat(seenEvents).as("a refused Hello publishes no membership event")
                .noneMatch(e -> e instanceof BusEvent.NodeJoined || e instanceof BusEvent.NodeLeft);
        assertThat(lifecycle.view()).hasSize(1);
        assertThat(lifecycle.zones()).containsEntry("inst-A1", "EU");
    }

    @Test
    void theTeardownOfAReplacedSessionKeepsTheZoneItsSuccessorRecorded() {
        var first = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        first.sayHello(HandshakeProtocol.PROTOCOL_VERSION, Set.of(HandshakeProtocol.zoneCapability("EU")))
                .orTimeout(2, TimeUnit.SECONDS).join();
        var second = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        second.sayHello(HandshakeProtocol.PROTOCOL_VERSION, Set.of(HandshakeProtocol.zoneCapability("AM")))
                .orTimeout(2, TimeUnit.SECONDS).join();
        // The superseded session is closed by the registry; its teardown (token mismatch, or,
        // on another event loop, a successful unregister before the successor registered) must
        // not forget the successor's zone.
        first.clientSide.close();
        await().atMost(2, TimeUnit.SECONDS).until(() -> lifecycle.view().size() == 1);
        assertThat(lifecycle.zones()).containsEntry("inst-A1", "AM");

        // The zone is token-scoped: only the session that recorded it can forget it.
        var node = lifecycle.view().iterator().next();
        assertThat(cluster.forgetZone(node, "some-older-token")).isFalse();
        assertThat(lifecycle.zones()).containsEntry("inst-A1", "AM");
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

    /** O3: every request the broker answers is timed by kind, payload and outcome. */
    @Test
    void theBrokerTimesEachAnsweredRequestByKindPayloadAndOutcome() throws Exception {
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        new com.evento.server.bus.spring.RequestMetrics(lifecycle, registry);
        var caller = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        var handler = new MockBundle("bundle-B", "inst-B1", lifecycle, transportServer);
        completeHandshake(caller);
        completeHandshake(handler);
        handler.registerHandlers(payloadCodec, List.of("com.FindDemo"));
        handler.clientSide.send(new Notification(UUID.randomUUID(), BundleDiscoveryInfo.PAYLOAD_TYPE,
                payloadCodec.encode(new BundleDiscoveryInfo(100L, "", "", "", "L",
                        List.of(new com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler(
                                com.evento.common.modeling.bundle.types.ComponentType.Projection, "DemoProjection",
                                com.evento.common.modeling.bundle.types.HandlerType.QueryHandler,
                                com.evento.common.modeling.bundle.types.PayloadType.Query,
                                "com.FindDemo", "DemoView", false, null)),
                        Map.of())),
                System.currentTimeMillis()));
        handler.enable();
        caller.enable();
        await().atMost(2, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2
                && seenEvents.stream().anyMatch(e -> e instanceof BusEvent.BundleDiscovered));
        handler.requestHandler = req -> handler.replyToRequest(req, new byte[0], "DemoView");

        assertThat(caller.sendRequest("com.FindDemo", new byte[0]).get(3, TimeUnit.SECONDS).isError()).isFalse();
        assertThat(caller.sendRequest("com.NobodyHandlesThis", new byte[0]).get(3, TimeUnit.SECONDS).isError()).isTrue();

        var query = registry.get("evento.server.request")
                .tags("kind", "query", "payload", "com.FindDemo", "outcome", "ok").timer();
        assertThat(query.count()).isEqualTo(1);
        assertThat(query.totalTime(TimeUnit.NANOSECONDS)).isPositive();
        assertThat(registry.get("evento.server.request")
                .tags("kind", "other", "payload", "com.NobodyHandlesThis", "outcome", "error").timer().count())
                .isEqualTo(1);
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

    /**
     * S15: a consumer whose clock runs 40 s behind the server still gets its events. The fetch's
     * staleness gate used to compare the consumer's timestamp with the server's clock and drop
     * every fetch of such a consumer; it now measures the time the fetch spent on this server.
     */
    @Test
    void aFetchFromAConsumerWhoseClockIsBehindIsServed() throws Exception {
        var store = org.mockito.Mockito.mock(com.evento.server.es.EventStore.class);
        org.mockito.Mockito.when(store.fetchEvents(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        new com.evento.server.es.EventStoreRequestHandlerBridge(lifecycle, store,
                new com.evento.server.es.ConsumerLagMetrics(registry, store,
                        com.evento.server.es.EventStoreMode.CPES, Duration.ofMinutes(15)),
                4, 30_000);
        var consumer = new MockBundle("projector-bundle", "proj-1", lifecycle, transportServer);
        completeHandshake(consumer);
        consumer.enable();
        await().atMost(2, TimeUnit.SECONDS).until(() -> lifecycle.isBundleAvailable("projector-bundle"));

        var codec = new com.evento.common.admin.AdminPayloadCodec();
        var request = new com.evento.common.modeling.messaging.message.internal.EventoRequest();
        request.setCorrelationId(UUID.randomUUID().toString());
        request.setSourceBundleId("projector-bundle");
        request.setBody(new com.evento.common.messaging.consumer.EventFetchRequest("default", 0, 10, "OrderProjector"));
        request.setTimestamp(System.currentTimeMillis() - 40_000);  // the consumer's clock, 40 s behind

        Response response = consumer.sendRequest("EventFetchRequest", codec.encodeRequest(request))
                .get(5, TimeUnit.SECONDS);
        assertThat(response.isError()).as("%s", response.error()).isFalse();
        assertThat(codec.decodeResponse(response.payload()).getBody())
                .isInstanceOf(com.evento.common.messaging.consumer.EventFetchResponse.class);
    }

    /**
     * Q4: a connection that closes while its Hello is still being handled. Its disconnect found
     * no address and cleaned up nothing; the Hello then registered the node on a dead channel,
     * which stayed in the broker's view for good (a bundle stopped mid-failback stayed
     * "registered" on the broker it was switching to).
     */
    @Test
    void aHelloHandledAfterItsConnectionClosedLeavesNoNodeBehind() {
        var transport = new ScriptedTransport();
        lifecycle.acceptConnection(transport);
        transport.closeNow();
        transport.deliver(new Hello(UUID.randomUUID(), HandshakeProtocol.PROTOCOL_VERSION,
                "late-bundle", "late-1", "100", Set.of(), null, System.currentTimeMillis()));

        // The Hello may be handled on a dispatch thread: give it time, then the node must not be there.
        await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(lifecycle.view()).as("no phantom node").noneMatch(a -> a.instanceId().equals("late-1")));
        assertThat(seenEvents).noneMatch(e -> e instanceof BusEvent.NodeJoined joined
                && joined.node().instanceId().equals("late-1")
                && seenEvents.stream().noneMatch(l -> l instanceof BusEvent.NodeLeft left
                        && left.node().instanceId().equals("late-1")));
    }

    /** A transport whose close and inbound messages the test orders by hand. */
    static final class ScriptedTransport implements Transport {
        private Consumer<Message> inbound = m -> { };
        private java.util.function.BiConsumer<com.evento.transport.state.ConnectionState,
                com.evento.transport.state.ConnectionState> stateListener = (a, b) -> { };
        private volatile com.evento.transport.state.ConnectionState state =
                com.evento.transport.state.ConnectionState.CONNECTED;

        void closeNow() { close(); }

        void deliver(Message message) { inbound.accept(message); }

        @Override public String remoteId() { return "scripted"; }
        @Override public com.evento.transport.state.ConnectionState state() { return state; }
        @Override public CompletableFuture<Void> connect() { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> send(Message message) { return CompletableFuture.completedFuture(null); }
        @Override public void onMessage(Consumer<Message> handler) { inbound = handler; }
        @Override public void onStateChange(java.util.function.BiConsumer<com.evento.transport.state.ConnectionState,
                com.evento.transport.state.ConnectionState> listener) { stateListener = listener; }
        @Override public long lastInboundMs() { return System.currentTimeMillis(); }
        @Override public void close() {
            var before = state;
            state = com.evento.transport.state.ConnectionState.CLOSED;
            stateListener.accept(before, state);
        }
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
    void bundleDiscoveredEventCarriesFullDiscoveryInfo() {
        // The two-phase registration protocol: lean BundleRegistrationInfo first
        // (instant routing), then BundleDiscoveryInfo after enable (rich metadata).
        var bundle = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        completeHandshake(bundle);

        // Step 1: lean registration
        var lean = new BundleRegistrationInfo(100L, List.of("com.Foo"));
        bundle.clientSide.send(new Notification(UUID.randomUUID(),
                BundleRegistrationInfo.PAYLOAD_TYPE,
                payloadCodec.encode(lean), System.currentTimeMillis()));

        // BundleRegistered (lean) must fire
        await().atMost(2, TimeUnit.SECONDS).until(() ->
                seenEvents.stream().anyMatch(e -> e instanceof BusEvent.BundleRegistered));
        var registered = (BusEvent.BundleRegistered) seenEvents.stream()
                .filter(e -> e instanceof BusEvent.BundleRegistered).findFirst().orElseThrow();
        assertThat(registered.node().instanceId()).isEqualTo("inst-A1");
        assertThat(registered.registration().handlerPayloadTypes()).containsExactly("com.Foo");

        // Step 2: rich discovery notification (sent after routing is live)
        var discovery = new BundleDiscoveryInfo(
                100L, "", "", "", "L",
                List.of(),
                Map.of("com.Foo", new com.evento.transport.protocol.PayloadDiscoveryInfo(
                        "{\"type\":\"object\"}", "demo-domain", "", "", "", 0)));
        bundle.clientSide.send(new Notification(UUID.randomUUID(),
                BundleDiscoveryInfo.PAYLOAD_TYPE,
                payloadCodec.encode(discovery), System.currentTimeMillis()));

        await().atMost(2, TimeUnit.SECONDS).until(() ->
                seenEvents.stream().anyMatch(e -> e instanceof BusEvent.BundleDiscovered));
        var discovered = (BusEvent.BundleDiscovered) seenEvents.stream()
                .filter(e -> e instanceof BusEvent.BundleDiscovered).findFirst().orElseThrow();
        assertThat(discovered.node().instanceId()).isEqualTo("inst-A1");
        assertThat(discovered.discovery().payloadInfo()).containsKey("com.Foo");
        assertThat(discovered.discovery().payloadInfo().get("com.Foo").domain()).isEqualTo("demo-domain");
    }

    @Test
    void serverInitiatedForwardReachesTargetAndReturnsResponse() throws Exception {
        // BusFacade.forward sits on top of this primitive. The server originates
        // a request directly to a chosen NodeAddress and gets back the response
        // (no routing table involved).
        var bundle = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        completeHandshake(bundle);
        bundle.enable();
        await().atMost(2, TimeUnit.SECONDS).until(() -> lifecycle.isBundleAvailable("bundle-A"));

        var receivedRequests = new ArrayList<Request>();
        bundle.requestHandler = req -> {
            receivedRequests.add(req);
            bundle.replyToRequest(req, new byte[]{0x4F, 0x4B}, "test:reply");
        };

        var target = lifecycle.view().iterator().next();
        var responseFuture = lifecycle.forward(target, "evento:server-admin-request",
                new byte[]{1, 2, 3}, Duration.ofSeconds(2));
        var response = responseFuture.get(2, TimeUnit.SECONDS);

        assertThat(response.isError()).isFalse();
        assertThat(response.payloadType()).isEqualTo("test:reply");
        assertThat(response.payload()).containsExactly(0x4F, 0x4B);
        assertThat(receivedRequests).hasSize(1);
        assertThat(receivedRequests.getFirst().payloadType()).isEqualTo("evento:server-admin-request");
        assertThat(receivedRequests.getFirst().payload()).containsExactly(1, 2, 3);
        assertThat(receivedRequests.getFirst().sourceBundleId()).isEqualTo(BusLifecycle.SERVER_BUNDLE_ID);
        assertThat(receivedRequests.getFirst().sourceInstanceId()).isEqualTo("server-1");
    }

    @Test
    void forwardToUnknownDestinationFailsImmediately() {
        var ghost = new com.evento.server.bus.NodeAddress("nope", 1L, "ghost");
        var fut = lifecycle.forward(ghost, "evento:server-admin-request",
                new byte[0], Duration.ofSeconds(1));
        assertThat(fut).isCompletedExceptionally();
        assertThatThrownBy(fut::get).hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no connection for ghost");
    }

    @Test
    void waitUntilAvailableReturnsTrueWhenBundleBecomesAvailable() {
        var bundle = new MockBundle("bundle-A", "inst-A1", lifecycle, transportServer);
        completeHandshake(bundle);

        // Schedule enable just after waitUntilAvailable starts.
        try (var pool = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()) {
            pool.schedule(bundle::enable, 200, TimeUnit.MILLISECONDS);
            boolean ready = lifecycle.waitUntilAvailable("bundle-A", Duration.ofSeconds(2));
            assertThat(ready).isTrue();
        }
    }

    @Test
    void waitUntilAvailableReturnsFalseOnTimeout() {
        boolean ready = lifecycle.waitUntilAvailable("never-connects", Duration.ofMillis(200));
        assertThat(ready).isFalse();
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
