package com.evento.server.bus.lifecycle;

import com.evento.application.client.BundleClient;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.correlation.ForwardingDedupCache;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.bus.security.TokenValidator;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Replies are compressed end to end: the caller says it can decode them, the handler
 * compresses a large reply, the broker relays the compressed bytes untouched, and the
 * caller hands its application the original bytes.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class CompressedReplyIT {

    private static final String COMMAND = "ExportReport";
    private static final int SIZE = 2 * 1024 * 1024;

    private BusLifecycle lifecycle;
    private ForwardingDedupCache dedup;
    private int port;

    @BeforeEach
    void setUp() {
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        // A response over 1 MiB is not retained for replay: what the broker retains shows
        // whether the bytes it relayed were compressed.
        dedup = new ForwardingDedupCache(1000, Duration.ofMinutes(1), 64L * 1024 * 1024, 1024 * 1024);
        lifecycle = new BusLifecycle(new NettyServerTransport(NettyTransportConfig.defaults()),
                connections, new ClusterRegistry(connections), new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, "broker-compress", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                new JacksonCborPayloadCodec(), TokenValidator.acceptAll(), dedup);
        port = lifecycle.start(0);
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(500));
    }

    private static byte[] report() {
        var row = "2026-10-15;ORD-0001;EUR;129.90;SHIPPED\n".getBytes(StandardCharsets.UTF_8);
        var out = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) out[i] = row[i % row.length];
        return out;
    }

    private BundleClient client(String id, List<String> handled, boolean acceptCompressed) {
        return BundleClient.builder(id, id + "-1")
                .host("127.0.0.1").port(port)
                .handlerPayloadTypes(handled)
                .acceptCompressedReplies(acceptCompressed)
                .transportConfig(NettyTransportConfig.defaults())
                .build();
    }

    @Test
    void aLargeReplyCrossesTheBrokerCompressedAndArrivesIntact() throws Exception {
        var expected = report();
        try (var handler = client("reports", List.of(COMMAND), true);
             var caller = client("web", List.of(), true)) {
            handler.registerRequestHandler(COMMAND, (p, ctx) -> report());
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            var response = caller.request(COMMAND, new byte[]{1}).get(10, TimeUnit.SECONDS);

            assertThat(response.isError()).isFalse();
            assertThat(response.payload()).isEqualTo(expected);
            assertThat(dedup.retainedBytes()).isBetween(1L, 256L * 1024);
        }
    }

    @Test
    void aCallerThatDoesNotAcceptCompressionGetsThePlainBytes() throws Exception {
        var expected = report();
        try (var handler = client("reports", List.of(COMMAND), true);
             var caller = client("legacy-web", List.of(), false)) {
            handler.registerRequestHandler(COMMAND, (p, ctx) -> report());
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            var response = caller.request(COMMAND, new byte[]{1}).get(10, TimeUnit.SECONDS);

            assertThat(response.isError()).isFalse();
            assertThat(response.payload()).isEqualTo(expected);
            // 2 MiB went through uncompressed: too large to retain.
            assertThat(dedup.retainedBytes()).isZero();
        }
    }
}
