package com.evento.lab.support;

import com.evento.application.client.v2.BundleClient;
import com.evento.application.client.v2.EventoServerV2Adapter;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.CommandGatewayImpl;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.messaging.gateway.QueryGatewayImpl;
import com.evento.server.bus.v2.correlation.CorrelationStore;
import com.evento.server.bus.v2.event.BusEventBus;
import com.evento.server.bus.v2.lifecycle.BusLifecycle;
import com.evento.server.bus.v2.registry.ClusterRegistry;
import com.evento.server.bus.v2.registry.ConnectionRegistry;
import com.evento.server.bus.v2.router.ForwardingTable;
import com.evento.server.es.CommandBrokerHandler;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Embedded broker variant that wires {@link CommandBrokerHandler} with an
 * in-memory {@link CommandAwareTestEventStore}, enabling full command→event-store→projector
 * integration tests without a real PostgreSQL database.
 *
 * <p>The shared in-memory store is used both by {@code CommandBrokerHandler}
 * (writes events after aggregate command processing) and by the
 * {@link CommandAwareTestEventStore} BundleClient handler (serves
 * {@code EventFetchRequest} to projector consumer engines).
 */
public final class CommandAwareEmbeddedBroker implements AutoCloseable {

    private final BusLifecycle lifecycle;
    private final int port;
    private final CommandAwareTestEventStore eventStore;

    public CommandAwareEmbeddedBroker() throws Exception {
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

        // Event store connects as a BundleClient AFTER broker starts
        eventStore = new CommandAwareTestEventStore(port);

        // Wire command broker — null DataSource is safe when commands carry no lockId
        new CommandBrokerHandler(lifecycle, cluster, eventStore, null);
    }

    public int port() { return port; }
    public BusLifecycle lifecycle() { return lifecycle; }
    public CommandAwareTestEventStore eventStore() { return eventStore; }

    /**
     * Creates a lightweight gateway client for sending commands and queries from tests.
     * The caller is responsible for closing it when done.
     */
    public TestGatewayClient newGatewayClient() throws Exception {
        var client = BundleClient.builder("test-gateway-client", UUID.randomUUID().toString())
                .host("127.0.0.1")
                .port(port)
                .bundleVersion("1")
                .handlerPayloadTypes(List.of())
                .build();
        client.start().get(10, TimeUnit.SECONDS);
        var server = new EventoServerV2Adapter(client, "test-gateway-client", "tc-1", 1L);
        return new TestGatewayClient(client, new CommandGatewayImpl(server), new QueryGatewayImpl(server));
    }

    @Override
    public void close() throws Exception {
        eventStore.close();
        lifecycle.stop(Duration.ofSeconds(5));
    }

    public record TestGatewayClient(
            BundleClient client,
            CommandGateway commandGateway,
            QueryGateway queryGateway) implements AutoCloseable {

        @Override
        public void close() {
            client.stop(Duration.ofSeconds(5));
        }
    }
}
