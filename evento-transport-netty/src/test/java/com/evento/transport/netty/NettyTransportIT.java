package com.evento.transport.netty;

import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.codec.PayloadCodec;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import com.evento.transport.message.ResponseError;
import com.evento.transport.state.ConnectionState;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration: real Netty client + server on localhost,
 * exchanging realistic command/event payloads.
 */
class NettyTransportIT {

    /** Payload shapes mirror the demo bundle (DemoCreateCommand / DemoCreatedEvent). */
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

    private NettyServerTransport server;
    private NettyClientTransport client;
    private PayloadCodec payloadCodec;
    private NettyTransportConfig config;
    private CopyOnWriteArrayList<com.evento.transport.Transport> serverChildren;

    @BeforeEach
    void setUp() {
        // Tight heartbeat for fast feedback in tests.
        config = new NettyTransportConfig(
                Duration.ofMillis(500),
                Duration.ofMillis(2000),
                Duration.ofSeconds(5),
                16 * 1024 * 1024,
                64 * 1024, 32 * 1024,
                new com.evento.transport.reconnect.ExponentialBackoffWithJitter(),
                new com.evento.transport.codec.JacksonCborCodec(),
                java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
        );
        payloadCodec = new JacksonCborPayloadCodec();
        serverChildren = new CopyOnWriteArrayList<>();
        server = new NettyServerTransport(config);
        server.onConnection(t -> serverChildren.add(t));
        int port = server.start(0);
        client = new NettyClientTransport("client-1", "127.0.0.1", port, config);
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    @Test
    void connectAndStateMoveToConnected() {
        client.connect().join();
        assertThat(client.state()).isEqualTo(ConnectionState.CONNECTED);
        await().atMost(2, TimeUnit.SECONDS).until(() -> !serverChildren.isEmpty());
        assertThat(serverChildren.getFirst().state()).isEqualTo(ConnectionState.CONNECTED);
    }

    @Test
    void roundTripCommandToEventResponse() throws Exception {
        var pending = new ConcurrentHashMap<UUID, CompletableFuture<Response>>();
        client.onMessage(msg -> {
            if (msg instanceof Response r) {
                var f = pending.remove(r.correlationId());
                if (f != null) f.complete(r);
            }
        });
        client.connect().join();

        await().atMost(2, TimeUnit.SECONDS).until(() -> !serverChildren.isEmpty());
        var serverChild = serverChildren.getFirst();
        serverChild.onMessage(msg -> {
            if (!(msg instanceof Request req)) return;
            CreateDemoCommand cmd = payloadCodec.decode(req.payload(), CreateDemoCommand.class);
            var event = new DemoCreatedEvent(cmd.demoId(), cmd.name(), cmd.value(), 1_700_000_000_000L);
            var resp = Response.success(req.correlationId(), DemoCreatedEvent.class.getName(),
                    payloadCodec.encode(event));
            serverChild.send(resp);
        });

        var cmd = new CreateDemoCommand("demo-42", "Hello", 99L);
        var corr = UUID.randomUUID();
        var future = new CompletableFuture<Response>();
        pending.put(corr, future);

        var req = new Request(corr, "client-1", "i1", "1.0.0",
                CreateDemoCommand.class.getName(), payloadCodec.encode(cmd),
                5_000L, System.currentTimeMillis());
        client.send(req).join();

        Response response = future.get(5, TimeUnit.SECONDS);
        assertThat(response.isError()).isFalse();
        assertThat(response.correlationId()).isEqualTo(corr);
        var decoded = payloadCodec.decode(response.payload(), DemoCreatedEvent.class);
        assertThat(decoded.demoId()).isEqualTo("demo-42");
        assertThat(decoded.name()).isEqualTo("Hello");
        assertThat(decoded.value()).isEqualTo(99L);
    }

    @Test
    void serverErrorPropagatesAsResponseError() throws Exception {
        var pending = new ConcurrentHashMap<UUID, CompletableFuture<Response>>();
        client.onMessage(msg -> {
            if (msg instanceof Response r) {
                var f = pending.remove(r.correlationId());
                if (f != null) f.complete(r);
            }
        });
        client.connect().join();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !serverChildren.isEmpty());
        var serverChild = serverChildren.getFirst();
        serverChild.onMessage(msg -> {
            if (!(msg instanceof Request req)) return;
            var err = Response.failure(req.correlationId(),
                    ResponseError.of(new IllegalStateException("nope")));
            serverChild.send(err);
        });

        var corr = UUID.randomUUID();
        var future = new CompletableFuture<Response>();
        pending.put(corr, future);
        client.send(new Request(corr, "client-1", "i1", "1.0.0",
                CreateDemoCommand.class.getName(),
                payloadCodec.encode(new CreateDemoCommand("x", "y", 0L)),
                5_000L, System.currentTimeMillis())).join();

        Response r = future.get(5, TimeUnit.SECONDS);
        assertThat(r.isError()).isTrue();
        assertThat(r.error().exceptionClassName()).isEqualTo(IllegalStateException.class.getName());
        assertThat(r.error().message()).isEqualTo("nope");
    }

    @Test
    void parallelRequestsPreserveCorrelation() throws Exception {
        var pending = new ConcurrentHashMap<UUID, CompletableFuture<Response>>();
        client.onMessage(msg -> {
            if (msg instanceof Response r) {
                var f = pending.remove(r.correlationId());
                if (f != null) f.complete(r);
            }
        });
        client.connect().join();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !serverChildren.isEmpty());
        var serverChild = serverChildren.getFirst();
        serverChild.onMessage(msg -> {
            if (!(msg instanceof Request req)) return;
            CreateDemoCommand cmd = payloadCodec.decode(req.payload(), CreateDemoCommand.class);
            var event = new DemoCreatedEvent(cmd.demoId(), cmd.name(), cmd.value() * 2, 0L);
            serverChild.send(Response.success(req.correlationId(), DemoCreatedEvent.class.getName(),
                    payloadCodec.encode(event)));
        });

        int n = 32;
        @SuppressWarnings("unchecked")
        CompletableFuture<Response>[] futures = new CompletableFuture[n];
        UUID[] ids = new UUID[n];
        for (int i = 0; i < n; i++) {
            ids[i] = UUID.randomUUID();
            futures[i] = new CompletableFuture<>();
            pending.put(ids[i], futures[i]);
            client.send(new Request(ids[i], "client-1", "i1", "1.0.0",
                    CreateDemoCommand.class.getName(),
                    payloadCodec.encode(new CreateDemoCommand("d-" + i, "n", i)),
                    5_000L, System.currentTimeMillis()));
        }
        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
        for (int i = 0; i < n; i++) {
            Response r = futures[i].get();
            assertThat(r.correlationId()).isEqualTo(ids[i]);
            var decoded = payloadCodec.decode(r.payload(), DemoCreatedEvent.class);
            assertThat(decoded.value()).isEqualTo(i * 2L);
            assertThat(decoded.demoId()).isEqualTo("d-" + i);
        }
    }

    @Test
    void clientStateMovesToDisconnectedWhenServerCloses() {
        client.connect().join();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !serverChildren.isEmpty());
        serverChildren.getFirst().close();
        await().atMost(2, TimeUnit.SECONDS).until(() -> client.state() == ConnectionState.DISCONNECTED);
    }

    @Test
    void heartbeatPingFlowsBetweenIdlePeers() throws Exception {
        // Capture inbound messages on the server child to confirm Ping/Pong cycle.
        // HeartbeatHandler swallows Ping at the handler level; we observe activity via lastInboundMs only.
        var serverLastInbound = new AtomicReference<Long>(0L);
        client.connect().join();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !serverChildren.isEmpty());
        var serverChild = serverChildren.getFirst();
        serverChild.onMessage(m -> {});
        client.onMessage(m -> {});

        // Wait beyond writer-idle (500ms) but within reader-idle (2000ms).
        long t0 = serverChild.lastInboundMs();
        await().atMost(3, TimeUnit.SECONDS).until(() -> serverChild.lastInboundMs() > t0);
        serverLastInbound.set(serverChild.lastInboundMs());
        assertThat(serverChild.state()).isEqualTo(ConnectionState.CONNECTED);
        assertThat(client.state()).isEqualTo(ConnectionState.CONNECTED);
    }

    @Test
    void abruptClientCloseSurfacesAsDisconnectOnServerChild() {
        client.connect().join();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !serverChildren.isEmpty());
        var serverChild = serverChildren.getFirst();
        client.close();
        await().atMost(2, TimeUnit.SECONDS).until(() ->
                serverChild.state() == ConnectionState.DISCONNECTED ||
                serverChild.state() == ConnectionState.CLOSED);
    }

    @Test
    @Timeout(15)
    void childCloseReturnsWhenEventLoopIsWedged() throws Exception {
        // Regression for the prod hang: close() used to syncUninterruptibly() on the
        // close promise; when the channel's event loop never serviced the close (a
        // half-dead superseded connection), the calling thread — a server bus worker —
        // blocked forever, leaking its DB transaction and pool connection. close() is
        // now bounded by connectTimeout.
        var quickConfig = new NettyTransportConfig(
                Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofSeconds(1),
                16 * 1024 * 1024, 64 * 1024, 32 * 1024,
                new com.evento.transport.reconnect.ExponentialBackoffWithJitter(),
                new com.evento.transport.codec.JacksonCborCodec(),
                java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        var wedgeServer = new NettyServerTransport(quickConfig);
        var children = new CopyOnWriteArrayList<com.evento.transport.Transport>();
        wedgeServer.onConnection(children::add);
        int port = wedgeServer.start(0);
        var wedgeClient = new NettyClientTransport("wedge-client", "127.0.0.1", port, quickConfig);
        var wedge = new CountDownLatch(1);
        try {
            wedgeClient.connect().join();
            await().atMost(2, TimeUnit.SECONDS).until(() -> !children.isEmpty());
            var child = children.getFirst();

            // Wedge the child channel's event loop so the close op is never processed.
            var channelField = child.getClass().getDeclaredField("channel");
            channelField.setAccessible(true);
            var channel = (io.netty.channel.Channel) channelField.get(child);
            channel.eventLoop().execute(() -> {
                try {
                    wedge.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });

            long t0 = System.nanoTime();
            child.close();
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

            assertThat(child.state()).isEqualTo(ConnectionState.CLOSED);
            assertThat(elapsedMs).isLessThan(5_000);
        } finally {
            wedge.countDown();
            wedgeClient.close();
            wedgeServer.stop();
        }
    }

    @Test
    void largePayloadTransparentlyChunked() throws Exception {
        // Use a small chunk size to exercise the chunking path with many frames.
        int chunkSize = 64 * 1024; // 64 KB
        var chunkConfig = new NettyTransportConfig(
                Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofSeconds(5),
                chunkSize, 64 * 1024, 32 * 1024,
                new com.evento.transport.reconnect.ExponentialBackoffWithJitter(),
                new com.evento.transport.codec.JacksonCborCodec(),
                java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());

        // Stand up a dedicated server/client pair with small chunk size.
        var chunkServer = new NettyServerTransport(chunkConfig);
        var chunkServerChildren = new CopyOnWriteArrayList<com.evento.transport.Transport>();
        chunkServer.onConnection(chunkServerChildren::add);
        int chunkPort = chunkServer.start(0);
        var chunkClient = new NettyClientTransport("chunk-client", "127.0.0.1", chunkPort, chunkConfig);
        try {
            // Build a 5 MB payload — well above the 64 KB chunk size → ~80 chunks.
            int payloadSize = 5 * 1024 * 1024;
            byte[] sentPayload = new byte[payloadSize];
            new java.util.Random(42).nextBytes(sentPayload);

            var pending = new ConcurrentHashMap<UUID, CompletableFuture<Response>>();
            chunkClient.onMessage(msg -> {
                if (msg instanceof Response r) {
                    var f = pending.remove(r.correlationId());
                    if (f != null) f.complete(r);
                }
            });
            chunkClient.connect().join();
            await().atMost(2, TimeUnit.SECONDS).until(() -> !chunkServerChildren.isEmpty());

            // Server echoes the payload back unchanged.
            chunkServerChildren.getFirst().onMessage(msg -> {
                if (!(msg instanceof Request req)) return;
                chunkServerChildren.getFirst().send(
                        Response.success(req.correlationId(), "echo", req.payload()));
            });

            var corr = UUID.randomUUID();
            var future = new CompletableFuture<Response>();
            pending.put(corr, future);
            chunkClient.send(new Request(corr, "chunk-client", "i1", "1.0",
                    "echo", sentPayload, 10_000L, System.currentTimeMillis())).join();

            Response response = future.get(10, TimeUnit.SECONDS);
            assertThat(response.isError()).isFalse();
            assertThat(response.payload()).isEqualTo(sentPayload);
        } finally {
            chunkClient.close();
            chunkServer.stop();
        }
    }
}
