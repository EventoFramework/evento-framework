package com.evento.lab.support;

import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.correlation.ForwardingDedupCache;
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
 * Spins up a real v2 broker on an ephemeral TCP port for integration tests.
 * Each test can create its own instance and close it when done.
 */
public final class EmbeddedBroker implements AutoCloseable {

    private final BusLifecycle lifecycle;
    private final BusEventBus eventBus;
    private final int port;

    public EmbeddedBroker() {
        this("lab-broker", 0);
    }

    /**
     * A broker announcing {@code instanceId} on {@code port} (0: ephemeral). Two of
     * them, or one restarted on the port of a stopped one, stand for a cluster's
     * servers in failover / fail-back tests.
     */
    public EmbeddedBroker(String instanceId, int port) {
        this(instanceId, port, Set.of());
    }

    /** As {@link #EmbeddedBroker(String, int)}, also offering {@code capabilities} (e.g. head notices). */
    public EmbeddedBroker(String instanceId, int port, Set<String> capabilities) {
        var eventBus = new BusEventBus();
        this.eventBus = eventBus;
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofSeconds(30));
        var forwarding = new ForwardingTable();
        var server = new NettyServerTransport(NettyTransportConfig.defaults());
        lifecycle = new BusLifecycle(
                server, connections, cluster, correlations, forwarding,
                eventBus, instanceId,
                with(capabilities, HandshakeProtocol.CAPABILITY_PING_PONG));
        this.port = lifecycle.start(port);
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

    /**
     * The broker's event stream. Subscribe before connecting a bundle to observe
     * {@code BusEvent.BundleDiscovered} and assert on the rich discovery metadata as it
     * actually arrived over the wire.
     */
    public BusEventBus eventBus() {
        return eventBus;
    }

    @Override
    public void close() {
        lifecycle.stop(Duration.ofSeconds(5));
    }
}
