package com.evento.server.bus.spring;

import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.correlation.ForwardingDedupCache;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.bus.security.TokenValidator;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** The broker's query-path meters (2.6) are bound next to the existing ones. */
class BusMetricsBinderTest {

    @Test
    void theStreamAndBufferMetersAreRegistered() {
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var forwarding = new ForwardingTable();
        var correlations = new CorrelationStore(Duration.ofSeconds(1));
        var bus = new BusLifecycle(new NettyServerTransport(NettyTransportConfig.defaults()), connections,
                new ClusterRegistry(connections),
                correlations, forwarding, eventBus, "metrics", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                new JacksonCborPayloadCodec(), TokenValidator.acceptAll(), new ForwardingDedupCache());
        var counter = new AtomicLong();
        var executor = new BusBusinessExecutor(1, 2, 1000L, new BusBusinessExecutor.GrowthFirstQueue(4),
                r -> new Thread(r, "metrics-test-" + counter.incrementAndGet()), 10_000L);
        try {
            var registry = new SimpleMeterRegistry();
            new BusMetricsBinder(bus, correlations, forwarding, executor).bindTo(registry);

            for (var name : new String[]{"evento.server.streams.active", "evento.server.reconnect.buffer.bytes",
                    "evento.server.dedup.retained.bytes"}) {
                assertThat(registry.get(name).gauge().value()).as(name).isZero();
            }
            for (var name : new String[]{"evento.server.streams.opened", "evento.server.stream.frames",
                    "evento.server.stream.bytes"}) {
                assertThat(registry.get(name).functionCounter().count()).as(name).isZero();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
