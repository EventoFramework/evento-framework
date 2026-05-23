package com.evento.lab.ms.it.support;

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
 * Embedded broker variant for ms integration tests that wires
 * {@link CommandBrokerHandler} with {@link MsCommandAwareTestEventStore}, enabling
 * full command→event-store→projector tests without a real PostgreSQL database.
 */
public final class MsCommandAwareEmbeddedBroker implements AutoCloseable {

    private final BusLifecycle lifecycle;
    private final int port;
    private final MsCommandAwareTestEventStore eventStore;

    public MsCommandAwareEmbeddedBroker() throws Exception {
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

        eventStore = new MsCommandAwareTestEventStore(port);
        new CommandBrokerHandler(lifecycle, cluster, eventStore, null);
    }

    public int port() { return port; }
    public BusLifecycle lifecycle() { return lifecycle; }
    public MsCommandAwareTestEventStore eventStore() { return eventStore; }

    /**
     * Creates a lightweight gateway client for sending commands and queries from tests.
     * The caller is responsible for closing it when done.
     */
    public TestGatewayClient newGatewayClient() throws Exception {
        var client = BundleClient.builder("ms-test-gateway-client", UUID.randomUUID().toString())
                .host("127.0.0.1")
                .port(port)
                .bundleVersion("1")
                .handlerPayloadTypes(List.of())
                .build();
        client.start().get(10, TimeUnit.SECONDS);
        var server = new EventoServerV2Adapter(client, "ms-test-gateway-client", "tc-1", 1L);
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
