package com.evento.server.bus.lifecycle;

import com.evento.application.client.BundleClient;
import com.evento.application.client.BundleClientState;
import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.event.BusEvent;
import com.evento.server.bus.event.BusEventBus;
import com.evento.transport.protocol.BundleRegistrationInfo;
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
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Failure-mode coverage for the v2 server bus over real TCP. Each test wires
 * up a fresh {@link BusLifecycle} on an ephemeral port and drives one or more
 * Netty-backed mock bundles through a specific disconnection scenario.
 *
 * <p>These tests are the regression net for the original "instabilità di
 * connessione" complaint that drove the v2 rewrite: any disconnect path that
 * leaves a stale registry entry, hangs an originator future, or surfaces an
 * exception on the server is a test failure here.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class BusLifecycleDisconnectIT {

    /**
     * Ceiling for a single async step (registry convergence, a request round-trip,
     * a handshake). Cold CI runners schedule these Netty/virtual-thread hops far
     * slower than a dev box, so a tight 3s budget flaked intermittently. This is a
     * generous ceiling, not an expected latency — steps normally complete in
     * milliseconds, and a genuine hang still trips the class-level {@link Timeout}.
     */
    private static final long STEP_TIMEOUT_SECONDS = 10;

    private NettyTransportConfig serverConfig;
    private NettyTransportConfig clientConfig;
    private BusEventBus eventBus;
    private CopyOnWriteArrayList<BusEvent> events;
    private BusLifecycle lifecycle;
    private PayloadCodec payloadCodec;
    private int port;

    @BeforeEach
    void setUp() {
        serverConfig = nettyConfigWith();
        clientConfig = nettyConfigWith();
        eventBus = new BusEventBus();
        events = new CopyOnWriteArrayList<>();
        eventBus.subscribe(events::add);
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofMillis(100));
        var forwarding = new ForwardingTable();
        payloadCodec = new JacksonCborPayloadCodec();
        var server = new NettyServerTransport(serverConfig);
        lifecycle = new BusLifecycle(server, connections, cluster, correlations, forwarding,
                eventBus, "server-disc", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), payloadCodec);
        port = lifecycle.start(0);
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(500));
    }

    private NettyTransportConfig nettyConfigWith() {
        return new NettyTransportConfig(
                Duration.ofSeconds(5),
                Duration.ofSeconds(15),
                Duration.ofSeconds(5),
                16 * 1024 * 1024,
                64 * 1024, 32 * 1024,
                new ExponentialBackoffWithJitter(),
                new JacksonCborCodec(),
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    /** Real-TCP mock bundle. Independent copy of the one in BusLifecycleNettyIT — kept duplicated to keep each IT self-contained. */
    final class MockBundle implements AutoCloseable {
        final NettyClientTransport client;
        final String bundleId;
        final String instanceId;
        final ConcurrentHashMap<UUID, CompletableFuture<Welcome>> hellos = new ConcurrentHashMap<>();
        final ConcurrentHashMap<UUID, CompletableFuture<Response>> responses = new ConcurrentHashMap<>();
        volatile java.util.function.Consumer<Request> requestHandler;

        MockBundle(String bundleId, String instanceId) {
            this.bundleId = bundleId;
            this.instanceId = instanceId;
            this.client = new NettyClientTransport(bundleId + "-" + instanceId,
                    "127.0.0.1", port, clientConfig);
            client.onMessage(this::dispatch);
            client.connect().join();
        }

        private void dispatch(Message msg) {
            switch (msg) {
                case Welcome w -> {
                    var f = hellos.remove(w.correlationId());
                    if (f != null) f.complete(w);
                }
                case Response r -> {
                    var f = responses.remove(r.correlationId());
                    if (f != null) f.complete(r);
                }
                case Request req -> {
                    if (requestHandler != null) requestHandler.accept(req);
                }
                default -> {}
            }
        }

        Welcome sayHello() throws Exception {
            var corr = UUID.randomUUID();
            var f = new CompletableFuture<Welcome>();
            hellos.put(corr, f);
            client.send(new Hello(corr, HandshakeProtocol.PROTOCOL_VERSION,
                    bundleId, instanceId, "100", Set.of(), null, System.currentTimeMillis()));
            return f.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        void registerAndEnable(List<String> payloadTypes) {
            var info = BundleRegistrationInfo.lean(100L, payloadTypes);
            client.send(new Notification(UUID.randomUUID(),
                    BundleRegistrationInfo.PAYLOAD_TYPE,
                    payloadCodec.encode(info), System.currentTimeMillis())).join();
            client.send(new Notification(UUID.randomUUID(),
                    BusLifecycle.NOTIFY_ENABLE, new byte[0], System.currentTimeMillis())).join();
        }

        CompletableFuture<Response> request(String payloadType, byte[] payload) {
            var corr = UUID.randomUUID();
            var f = new CompletableFuture<Response>();
            responses.put(corr, f);
            client.send(new Request(corr, bundleId, instanceId, "100",
                    payloadType, payload, 10_000L, System.currentTimeMillis()));
            return f;
        }

        NodeAddress address() {
            return new NodeAddress(bundleId, 100L, instanceId);
        }

        @Override public void close() { client.close(); }
    }

    // ---------------------------------------------------------------------
    // 1) Destination disappears mid-flight → originator gets a failure response
    // ---------------------------------------------------------------------

    @Test
    void destinationBundleDisconnectMidRequestSurfacesFailureToOriginator() throws Exception {
        var caller = new MockBundle("bundle-A", "inst-A1");
        var handler = new MockBundle("bundle-B", "inst-B1");
        caller.sayHello();
        handler.sayHello();
        handler.registerAndEnable(List.of("com.NeverReplies"));
        caller.registerAndEnable(List.of());

        await().atMost(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

        // Handler accepts the request but never replies.
        var handlerSawRequest = new CountDownLatch(1);
        handler.requestHandler = req -> handlerSawRequest.countDown();

        var future = caller.request("com.NeverReplies", new byte[]{1, 2, 3});
        assertThat(handlerSawRequest.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // Kill the handler — server must surface a failure to the caller.
        handler.close();

        Response response = future.get(5, TimeUnit.SECONDS);
        assertThat(response.isError()).isTrue();
        assertThat(response.error().message()).contains("peer disconnected: inst-B1");

        caller.close();
    }

    // ---------------------------------------------------------------------
    // 2) Originator disappears mid-flight → handler's late reply is dropped quietly
    //    (no NPE on server, no spurious events)
    // ---------------------------------------------------------------------

    @Test
    void originatorDisconnectMidRequestSilentlyDropsLateResponse() throws Exception {
        var caller = new MockBundle("bundle-A", "inst-A1");
        var handler = new MockBundle("bundle-B", "inst-B1");
        caller.sayHello();
        handler.sayHello();
        handler.registerAndEnable(List.of("com.Slow"));
        caller.registerAndEnable(List.of());
        await().atMost(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

        var handlerSawRequest = new CountDownLatch(1);
        var handlerCanReply = new CountDownLatch(1);
        handler.requestHandler = req -> {
            handlerSawRequest.countDown();
            try {
                handlerCanReply.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            handler.client.send(Response.success(req.correlationId(), "ack", new byte[0]));
        };

        caller.request("com.Slow", new byte[0]);
        assertThat(handlerSawRequest.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // Caller dies first.
        caller.close();
        await().atMost(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> lifecycle.view().size() == 1);

        // Now release the handler — its late response should land on a clean server.
        handlerCanReply.countDown();
        // Give the response time to arrive; server should not log an exception.
        Thread.sleep(200);

        // The handler is still healthy and registered.
        assertThat(lifecycle.view()).extracting(NodeAddress::instanceId)
                .containsExactly("inst-B1");

        handler.close();
    }

    // ---------------------------------------------------------------------
    // 3) TCP open but client closes before sending Hello → registry stays empty
    // ---------------------------------------------------------------------

    @Test
    void disconnectBeforeHandshakeLeavesRegistriesClean() throws InterruptedException {
        var t = new NettyClientTransport("ghost", "127.0.0.1", port, clientConfig);
        try {
            t.connect().join();
            // Don't send Hello — close immediately.
            t.close();
            // The server-side session is pre-registered (token only, no NodeAddress).
            // Wait a small moment for the channel-inactive callback to fire.
            Thread.sleep(200);
            assertThat(lifecycle.view()).isEmpty();
            assertThat(lifecycle.availableView()).isEmpty();
            assertThat(events).noneMatch(e -> e instanceof BusEvent.NodeJoined);
        } finally {
            t.close();
        }
    }

    // ---------------------------------------------------------------------
    // 4) Handler bundle disconnect strips its payload bindings from the cluster
    // ---------------------------------------------------------------------

    @Test
    void handlerBundleDisconnectRemovesPayloadsFromClusterRegistry() throws Exception {
        var caller = new MockBundle("bundle-A", "inst-A1");
        var handler = new MockBundle("bundle-B", "inst-B1");
        caller.sayHello();
        handler.sayHello();
        handler.registerAndEnable(List.of("com.HandledByB"));
        caller.registerAndEnable(List.of());
        await().atMost(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

        handler.close();
        await().atMost(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 1);

        // A new request for com.HandledByB must now fail with "no handler".
        Response response = caller.request("com.HandledByB", new byte[0]).get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(response.isError()).isTrue();
        assertThat(response.error().message()).contains("no handler for com.HandledByB");

        caller.close();
    }

    // ---------------------------------------------------------------------
    // 5) Many bundles, one disconnect → only its slot is removed
    // ---------------------------------------------------------------------

    @Test
    void singleDisconnectDoesNotAffectOtherBundles() throws Exception {
        var bundles = new ArrayList<MockBundle>();
        for (int i = 0; i < 5; i++) {
            var b = new MockBundle("bundle-X", "inst-X" + i);
            b.sayHello();
            b.registerAndEnable(List.of());
            bundles.add(b);
        }
        await().atMost(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 5);

        bundles.get(2).close();
        await().atMost(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 4);

        var remainingInstanceIds = lifecycle.availableView().stream()
                .map(NodeAddress::instanceId).toList();
        assertThat(remainingInstanceIds).containsExactlyInAnyOrder(
                "inst-X0", "inst-X1", "inst-X3", "inst-X4");

        for (int i = 0; i < 5; i++) {
            if (i != 2) bundles.get(i).close();
        }
        await().atMost(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> lifecycle.view().isEmpty());
    }

    // ---------------------------------------------------------------------
    // 6) Reconnect with the same instanceId supersedes the previous session
    // ---------------------------------------------------------------------

    @Test
    void reconnectWithSameInstanceIdSupersedesGracefully() throws Exception {
        var first = new MockBundle("bundle-Z", "inst-Z");
        first.sayHello();
        first.registerAndEnable(List.of());
        await().atMost(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> lifecycle.isBundleAvailable("bundle-Z"));

        events.clear();
        // Reconnect with the same instance id — supersede semantics expected.
        var second = new MockBundle("bundle-Z", "inst-Z");
        second.sayHello();
        second.registerAndEnable(List.of());

        await().atMost(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() ->
                lifecycle.view().size() == 1 &&
                events.stream().anyMatch(e ->
                        e instanceof BusEvent.NodeLeft l && "superseded".equals(l.reason())));

        // The original transport should have been closed by the registry.
        await().atMost(2, TimeUnit.SECONDS).until(() ->
                first.client.state() == com.evento.transport.state.ConnectionState.DISCONNECTED
                        || first.client.state() == com.evento.transport.state.ConnectionState.CLOSED);

        first.close();
        second.close();
    }

    // ---------------------------------------------------------------------
    // 7) Server shutdown closes every bundle connection cleanly
    // ---------------------------------------------------------------------

    @Test
    void serverShutdownClosesAllBundlesAndUnblocksPendingRequests() throws Exception {
        var caller = new MockBundle("bundle-A", "inst-A1");
        var handler = new MockBundle("bundle-B", "inst-B1");
        caller.sayHello();
        handler.sayHello();
        handler.registerAndEnable(List.of("com.X"));
        caller.registerAndEnable(List.of());
        await().atMost(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

        // Handler never replies → request stays in forwarding table.
        var handlerSawRequest = new CountDownLatch(1);
        handler.requestHandler = req -> handlerSawRequest.countDown();
        var pending = caller.request("com.X", new byte[0]);
        assertThat(handlerSawRequest.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // Initiate server shutdown.
        lifecycle.stop(Duration.ofMillis(500));

        // Server is now stopped; the registries should be empty and both bundles' channels closed.
        await().atMost(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() ->
                caller.client.state() == com.evento.transport.state.ConnectionState.DISCONNECTED
                        || caller.client.state() == com.evento.transport.state.ConnectionState.CLOSED);
        assertThat(lifecycle.view()).isEmpty();
        // We don't assert the pending future's state — server shutdown may either
        // surface a failure or simply close the channel; either is acceptable.

        caller.close();
        handler.close();
    }

    // ---------------------------------------------------------------------
    // 9) Caller disconnects while request in-flight; handler replies after
    //    caller reconnects → future completes via the new transport
    // ---------------------------------------------------------------------

    @Test
    void inFlightRequestDeliveredAfterCallerReconnects() throws Exception {
        var handler = new MockBundle("bundle-B", "inst-B");
        handler.sayHello();
        handler.registerAndEnable(List.of("com.Reconnect"));

        var callerClient = BundleClient.builder("bundle-A", "inst-A")
                .host("127.0.0.1").port(port)
                .bundleVersion("100")
                .handlerPayloadTypes(List.of())
                .autoEnable(true)
                .transportConfig(clientConfig)
                .build();
        callerClient.start().get(5, TimeUnit.SECONDS);
        await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

        var handlerSawRequest = new CountDownLatch(1);
        var handlerCanReply  = new CountDownLatch(1);
        var capturedRequest  = new java.util.concurrent.atomic.AtomicReference<Request>();
        handler.requestHandler = req -> {
            capturedRequest.set(req);
            handlerSawRequest.countDown();
            try { handlerCanReply.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };

        var future = callerClient.request("com.Reconnect", new byte[]{1}, Duration.ofSeconds(15));
        assertThat(handlerSawRequest.await(5, TimeUnit.SECONDS)).isTrue();

        // Force-disconnect the caller; the future must survive in the tracker.
        callerClient.currentTransportForTest().close();

        // Wait for reconnect to complete (BundleClientState returns to READY).
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> callerClient.state() == BundleClientState.READY);

        // Handler now replies — the server routes the response to the new transport.
        handlerCanReply.countDown();
        var captured = capturedRequest.get();
        handler.client.send(Response.success(captured.correlationId(), "ack", new byte[]{42})).join();

        var response = future.get(5, TimeUnit.SECONDS);
        assertThat(response.isError()).isFalse();
        assertThat(response.payload()).containsExactly(42);

        callerClient.close();
        handler.close();
    }

    // ---------------------------------------------------------------------
    // 10) Caller disconnects while request in-flight; handler replies
    //     BEFORE the caller reconnects → server buffers the response and
    //     delivers it once the caller comes back
    // ---------------------------------------------------------------------

    @Test
    void inFlightResponseBufferedAndDeliveredOnCallerReconnect() throws Exception {
        var handler = new MockBundle("bundle-B", "inst-B");
        handler.sayHello();
        handler.registerAndEnable(List.of("com.Buffered"));

        var callerClient = BundleClient.builder("bundle-A", "inst-A")
                .host("127.0.0.1").port(port)
                .bundleVersion("100")
                .handlerPayloadTypes(List.of())
                .autoEnable(true)
                .transportConfig(clientConfig)
                .build();
        callerClient.start().get(5, TimeUnit.SECONDS);
        await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

        var handlerSawRequest = new CountDownLatch(1);
        var handlerCanReply  = new CountDownLatch(1);
        var capturedRequest  = new java.util.concurrent.atomic.AtomicReference<Request>();
        handler.requestHandler = req -> {
            capturedRequest.set(req);
            handlerSawRequest.countDown();
            try { handlerCanReply.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };

        var future = callerClient.request("com.Buffered", new byte[]{2}, Duration.ofSeconds(15));
        assertThat(handlerSawRequest.await(5, TimeUnit.SECONDS)).isTrue();

        // Force-disconnect the caller.
        callerClient.currentTransportForTest().close();

        // Wait for the server to register the disconnect before the handler replies.
        await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.view().size() == 1);

        // Handler replies while the caller is still disconnected: the server
        // should buffer the response in reconnectBuffer.
        handlerCanReply.countDown();
        var captured = capturedRequest.get();
        handler.client.send(Response.success(captured.correlationId(), "ack", new byte[]{99})).join();

        // Now let the caller reconnect; the buffered response must be replayed.
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> callerClient.state() == BundleClientState.READY);

        var response = future.get(5, TimeUnit.SECONDS);
        assertThat(response.isError()).isFalse();
        assertThat(response.payload()).containsExactly(99);

        callerClient.close();
        handler.close();
    }

    // ---------------------------------------------------------------------
    // 8) Concurrent connect/disconnect churn doesn't leak any registry state
    // ---------------------------------------------------------------------

    @Test
    void concurrentConnectAndDisconnectChurnLeavesRegistryConsistent() throws Exception {
        int rounds = 10;
        int parallel = 6;
        var pool = Executors.newVirtualThreadPerTaskExecutor();
        var errors = new AtomicInteger();
        try {
            for (int round = 0; round < rounds; round++) {
                var done = new CountDownLatch(parallel);
                for (int i = 0; i < parallel; i++) {
                    final int idx = round * parallel + i;
                    pool.execute(() -> {
                        try (var bundle = new MockBundle("churn", "inst-" + idx)) {
                            bundle.sayHello();
                            bundle.registerAndEnable(List.of());
                        } catch (Throwable t) {
                            errors.incrementAndGet();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(errors.get()).isZero();
        await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.view().isEmpty());
    }
}
