package com.evento.lab.support;

import com.evento.server.bus.v2.correlation.CorrelationStore;
import com.evento.server.bus.v2.correlation.ForwardingDedupCache;
import com.evento.server.bus.v2.event.BusEventBus;
import com.evento.server.bus.v2.lifecycle.BusLifecycle;
import com.evento.server.bus.v2.registry.ClusterRegistry;
import com.evento.server.bus.v2.registry.ConnectionRegistry;
import com.evento.server.bus.v2.router.ForwardingTable;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;

import java.time.Duration;
import java.util.Set;

/**
 * Spins up a real v2 broker on an ephemeral TCP port for integration tests.
 * Each test can create its own instance and close it when done.
 */
public final class EmbeddedBroker implements AutoCloseable {

    private final BusLifecycle lifecycle;
    private final int port;

    public EmbeddedBroker() {
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofSeconds(30));
        var forwarding = new ForwardingTable();
        var server = new NettyServerTransport(NettyTransportConfig.defaults());
        lifecycle = new BusLifecycle(
                server, connections, cluster, correlations, forwarding,
                eventBus, "lab-broker",
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG));
        port = lifecycle.start(0);
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
