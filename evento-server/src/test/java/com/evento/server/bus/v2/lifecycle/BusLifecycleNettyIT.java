package com.evento.server.bus.v2.lifecycle;

import com.evento.server.bus.v2.correlation.CorrelationStore;
import com.evento.server.bus.v2.event.BusEventBus;
import com.evento.transport.protocol.BundleRegistrationInfo;
import com.evento.server.bus.v2.registry.ClusterRegistry;
import com.evento.server.bus.v2.registry.ConnectionRegistry;
import com.evento.server.bus.v2.router.ForwardingTable;
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
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
 * The v2 server bus exercised against the production Netty transport. Spins up
 * a {@link BusLifecycle} on an ephemeral TCP port, opens real
 * {@link NettyClientTransport}s, and runs the same handshake / register /
 * forward flow validated by the in-process integration test. Proves the wiring
 * between the routing engine and the network layer.
 */
class BusLifecycleNettyIT {

    record CreateDemoCommand(String demoId, String name, long value) {
        @JsonCreator
        CreateDemoCommand(
                @JsonProperty("demoId") String demoId,
                @JsonProperty("name") String name,
                @JsonProperty("value") long value
        ) { this.demoId = demoId; this.name = name; this.value = value; }
    }

    record DemoCreatedEvent(String demoId, String name, long value) {
        @JsonCreator
        DemoCreatedEvent(
                @JsonProperty("demoId") String demoId,
                @JsonProperty("name") String name,
                @JsonProperty("value") long value
        ) { this.demoId = demoId; this.name = name; this.value = value; }
    }

    private NettyTransportConfig serverConfig;
    private NettyTransportConfig clientConfig;
    private BusLifecycle lifecycle;
    private PayloadCodec payloadCodec;
    private int port;

    @BeforeEach
    void setUp() {
        serverConfig = nettyConfigWith(new JacksonCborCodec());
        clientConfig = nettyConfigWith(new JacksonCborCodec());
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofMillis(100));
        var forwarding = new ForwardingTable();
        payloadCodec = new JacksonCborPayloadCodec();
        var server = new NettyServerTransport(serverConfig);
        lifecycle = new BusLifecycle(server, connections, cluster, correlations, forwarding,
                eventBus, "server-it", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), payloadCodec);
        port = lifecycle.start(0);
        assertThat(port).isGreaterThan(0);
    }

    private NettyTransportConfig nettyConfigWith(JacksonCborCodec codec) {
        return new NettyTransportConfig(
                Duration.ofSeconds(5),
                Duration.ofSeconds(15),
                Duration.ofSeconds(5),
                16 * 1024 * 1024,
                64 * 1024, 32 * 1024,
                new ExponentialBackoffWithJitter(),
                codec,
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(500));
    }

    /** A tiny Netty-based mock bundle wrapper, similar to the InMemory one but over TCP. */
    final class NettyMockBundle implements AutoCloseable {
        final NettyClientTransport client;
        final String bundleId;
        final String instanceId;
        final ConcurrentHashMap<UUID, CompletableFuture<Welcome>> hellos = new ConcurrentHashMap<>();
        final ConcurrentHashMap<UUID, CompletableFuture<Response>> responses = new ConcurrentHashMap<>();
        volatile java.util.function.Consumer<Request> requestHandler;

        NettyMockBundle(String bundleId, String instanceId) {
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

        CompletableFuture<Welcome> sayHello() {
            var corr = UUID.randomUUID();
            var f = new CompletableFuture<Welcome>();
            hellos.put(corr, f);
            client.send(new Hello(corr, HandshakeProtocol.PROTOCOL_VERSION,
                    bundleId, instanceId, "100", Set.of(), null, System.currentTimeMillis()));
            return f;
        }

        void registerAndEnable(List<String> payloadTypes) {
            var info = new BundleRegistrationInfo(100L, payloadTypes, Map.of());
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

        @Override public void close() { client.close(); }
    }

    @Test
    void handshakeOverRealTcpRegistersBundleInTheView() throws Exception {
        try (var bundle = new NettyMockBundle("bundle-A", "inst-A1")) {
            var welcome = bundle.sayHello().get(3, TimeUnit.SECONDS);
            assertThat(welcome.serverInstanceId()).isEqualTo("server-it");
            await().atMost(3, TimeUnit.SECONDS).until(() -> lifecycle.view().size() == 1);
        }
        // After bundle close, the disconnection should propagate.
        await().atMost(3, TimeUnit.SECONDS).until(() -> lifecycle.view().isEmpty());
    }

    @Test
    void routedRequestFlowsOverRealTcp() throws Exception {
        try (var caller = new NettyMockBundle("bundle-A", "inst-A1");
             var handler = new NettyMockBundle("bundle-B", "inst-B1")) {

            caller.sayHello().get(3, TimeUnit.SECONDS);
            handler.sayHello().get(3, TimeUnit.SECONDS);
            handler.registerAndEnable(List.of("com.CreateDemo"));
            caller.registerAndEnable(List.of());  // caller doesn't handle anything

            await().atMost(3, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            handler.requestHandler = req -> {
                var cmd = payloadCodec.decode(req.payload(), CreateDemoCommand.class);
                var evt = new DemoCreatedEvent(cmd.demoId(), cmd.name(), cmd.value() * 7);
                handler.client.send(Response.success(req.correlationId(),
                        DemoCreatedEvent.class.getName(), payloadCodec.encode(evt)));
            };

            var cmd = new CreateDemoCommand("d-netty", "via-tcp", 6L);
            var response = caller.request("com.CreateDemo", payloadCodec.encode(cmd))
                    .get(5, TimeUnit.SECONDS);

            assertThat(response.isError()).isFalse();
            var decoded = payloadCodec.decode(response.payload(), DemoCreatedEvent.class);
            assertThat(decoded.demoId()).isEqualTo("d-netty");
            assertThat(decoded.value()).isEqualTo(42L);
        }
    }

    @Test
    void unknownPayloadReturnsErrorResponseOverRealTcp() throws Exception {
        try (var caller = new NettyMockBundle("bundle-A", "inst-A1")) {
            caller.sayHello().get(3, TimeUnit.SECONDS);
            caller.registerAndEnable(List.of());
            await().atMost(3, TimeUnit.SECONDS).until(() -> lifecycle.isBundleAvailable("bundle-A"));

            var response = caller.request("com.NopeHandler", new byte[0]).get(3, TimeUnit.SECONDS);
            assertThat(response.isError()).isTrue();
            assertThat(response.error().message()).contains("no handler for com.NopeHandler");
        }
    }
}
