package com.evento.lab.ms.it.support;

import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;

import java.time.Duration;
import java.util.Set;

/**
 * Spins up a real v2 broker on an ephemeral TCP port for ms integration tests.
 * Each test can create its own instance and close it when done.
 */
public final class MsEmbeddedBroker implements AutoCloseable {

    private final BusLifecycle lifecycle;
    private final int port;

    public MsEmbeddedBroker() {
        this(Set.of());
    }

    /** A broker that also offers {@code capabilities} (e.g. head notices). */
    public MsEmbeddedBroker(Set<String> capabilities) {
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofSeconds(30));
        var forwarding = new ForwardingTable();
        var server = new NettyServerTransport(NettyTransportConfig.defaults());
        lifecycle = new BusLifecycle(
                server, connections, cluster, correlations, forwarding,
                eventBus, "ms-broker",
                with(capabilities, HandshakeProtocol.CAPABILITY_PING_PONG));
        port = lifecycle.start(0);
    }

    private static Set<String> with(Set<String> capabilities, String more) {
        var all = new java.util.HashSet<>(capabilities);
        all.add(more);
        return all;
    }

    /** The actual TCP port the broker is listening on. */
    public int port() {
        return port;
    }

    public BusLifecycle lifecycle() {
        return lifecycle;
    }

    @Override
    public void close() {
        lifecycle.stop(Duration.ofSeconds(5));
    }
}
