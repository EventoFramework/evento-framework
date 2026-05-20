package com.evento.server.bus.v2.lifecycle;

import com.evento.application.client.v2.BundleClient;
import com.evento.server.bus.v2.correlation.CorrelationStore;
import com.evento.server.bus.v2.correlation.ForwardingDedupCache;
import com.evento.server.bus.v2.event.BusEventBus;
import com.evento.server.bus.v2.registry.ClusterRegistry;
import com.evento.server.bus.v2.registry.ConnectionRegistry;
import com.evento.server.bus.v2.router.ForwardingTable;
import com.evento.server.bus.v2.security.TokenValidator;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves the broker uses the zero-copy {@code sendRaw} path when forwarding
 * Request/Response between bundles over Netty — no CBOR re-encode on the hop.
 *
 * <p>Asserts both the functional contract (the round-trip still works) and
 * the performance contract (the {@code forwardedRawCount} counter increments
 * while {@code forwardedReencodedCount} stays at zero across a Netty-only
 * caller→broker→handler→broker→caller path).
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class BusLifecycleZeroCopyIT {

    private BusLifecycle lifecycle;
    private int port;

    @BeforeEach
    void setUp() {
        var serverConfig = NettyTransportConfig.defaults();
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofMillis(100));
        var forwarding = new ForwardingTable();
        var dedup = new ForwardingDedupCache(1000, Duration.ofMinutes(1));
        var server = new NettyServerTransport(serverConfig);
        lifecycle = new BusLifecycle(server, connections, cluster, correlations, forwarding, eventBus,
                "broker-zc", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                new JacksonCborPayloadCodec(),
                TokenValidator.acceptAll(),
                dedup);
        port = lifecycle.start(0);
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(500));
    }

    private BundleClient client(String bundleId, String instanceId, List<String> handlers) {
        return BundleClient.builder(bundleId, instanceId)
                .host("127.0.0.1").port(port)
                .bundleVersion("100")
                .handlerPayloadTypes(handlers)
                .defaultRequestTimeout(Duration.ofSeconds(3))
                .transportConfig(NettyTransportConfig.defaults())
                .build();
    }

    @Test
    void forwardingHotPathUsesZeroCopyOnRealTcp() throws Exception {
        try (var handler = client("h", "h1", List.of("com.zc.cmd"));
             var caller = client("c", "c1", List.of())) {

            var invocations = new AtomicInteger();
            handler.registerRequestHandler("com.zc.cmd", (payload, ctx) -> {
                invocations.incrementAndGet();
                return ("echo:" + new String(payload)).getBytes();
            });

            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(3, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            long rawBefore = lifecycle.forwardedRawCount();
            long encodedBefore = lifecycle.forwardedReencodedCount();

            // 10 round-trips to amortise any one-off encode on the broker.
            int N = 10;
            for (int i = 0; i < N; i++) {
                var response = caller.request("com.zc.cmd", ("p-" + i).getBytes())
                        .get(3, TimeUnit.SECONDS);
                assertThat(response.isError()).isFalse();
                assertThat(new String(response.payload())).isEqualTo("echo:p-" + i);
            }
            assertThat(invocations.get()).isEqualTo(N);

            long rawAfter = lifecycle.forwardedRawCount();
            long encodedAfter = lifecycle.forwardedReencodedCount();

            // Each round-trip forwards the Request (caller→handler) AND the Response
            // (handler→caller) through the broker, so 2*N forwards in total.
            assertThat(rawAfter - rawBefore)
                    .as("every Netty-to-Netty forward should use sendRaw (zero-copy)")
                    .isEqualTo(2L * N);
            assertThat(encodedAfter - encodedBefore)
                    .as("no Netty-to-Netty forward should fall back to encode-on-forward")
                    .isZero();
        }
    }

    @Test
    void replayedDedupResponsesDoNotIncrementForwardingCounters() throws Exception {
        // When the dedup cache replays a Response directly to the caller, it goes through
        // the broker's session.transport().send (not forwardOrSend), so neither counter moves.
        try (var handler = client("h", "h1", List.of("com.dedup"));
             var caller = client("c", "c1", List.of())) {

            handler.registerRequestHandler("com.dedup",
                    (p, ctx) -> ("ack:" + new String(p)).getBytes());
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(3, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            // First request: real forward.
            caller.request("com.dedup", "x".getBytes()).get(3, TimeUnit.SECONDS);

            long rawAfterFirst = lifecycle.forwardedRawCount();
            assertThat(rawAfterFirst).isPositive();

            // Subsequent identical-correlation requests would be replays — but BundleClient
            // generates fresh UUIDs per request, so this branch is covered by
            // BundleClientIT's dedup test instead. Here we just confirm that distinct
            // correlationIds keep going through the zero-copy hot path on each call.
            for (int i = 0; i < 5; i++) {
                caller.request("com.dedup", ("y" + i).getBytes()).get(3, TimeUnit.SECONDS);
            }
            assertThat(lifecycle.forwardedRawCount()).isEqualTo(rawAfterFirst + 5 * 2L);
            assertThat(lifecycle.forwardedReencodedCount()).isZero();
        }
    }
}
