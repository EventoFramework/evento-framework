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
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofSeconds(30));
        var forwarding = new ForwardingTable();
        var server = new NettyServerTransport(NettyTransportConfig.defaults());
        lifecycle = new BusLifecycle(
                server, connections, cluster, correlations, forwarding,
                eventBus, "ms-broker",
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
