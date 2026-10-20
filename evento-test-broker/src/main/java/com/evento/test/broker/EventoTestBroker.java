package com.evento.test.broker;

import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.messaging.consumer.EventFetchRequest;
import com.evento.common.messaging.consumer.EventFetchResponse;
import com.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
import com.evento.common.messaging.consumer.EventLastSequenceNumberResponse;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.es.CommandBrokerHandler;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * An Evento broker running in the test's JVM, on a free TCP port, with an in-memory event store:
 * the server's own routing ({@code BusLifecycle}) and aggregate command handling
 * ({@code CommandBrokerHandler}), so bundles under test connect to it as to a real server, their
 * aggregate commands are stored as events, and their projectors, sagas and observers read them.
 * No database, no Spring context; one broker per test is cheap.
 *
 * <pre>{@code
 * try (var broker = EventoTestBroker.start()) {
 *     var bundle = EventoBundle.Builder.builder()
 *             .setBasePackage(OrderAggregate.class.getPackage())
 *             .setBundleId("orders")
 *             .setEventoServerMessageBusConfiguration(broker.busConfiguration())
 *             .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
 *             .start();
 *     bundle.getCommandGateway().sendAndWait(new CreateOrder("o-1"));
 *     assertThat(broker.events()).hasSize(1);
 * }
 * }</pre>
 *
 * <p>What it leaves out: persistence (a restart loses everything), distributed locks (one JVM),
 * the web console and its API, clustering. For those, test against the real server
 * ({@code EventoServerContainer} in {@code evento-test}). Like the server, it needs Java 25.
 */
public final class EventoTestBroker implements AutoCloseable {

    private final BusLifecycle lifecycle;
    private final BusEventBus eventBus;
    private final InMemoryEventStore store = new InMemoryEventStore();
    private final int port;

    private EventoTestBroker(int port) {
        eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        lifecycle = new BusLifecycle(new NettyServerTransport(NettyTransportConfig.defaults()), connections, cluster,
                new CorrelationStore(Duration.ofSeconds(30)), new ForwardingTable(), eventBus, "evento-test-broker",
                // The same capabilities as the server (BusConfiguration); the rest is negotiated.
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG));
        var codec = new AdminPayloadCodec();
        // The event store's two requests, answered here as the server answers them.
        lifecycle.registerLocalHandler(EventFetchRequest.class.getSimpleName(), payload -> {
            var request = codec.decodeRequest(payload);
            var fetch = (EventFetchRequest) request.getBody();
            var response = new EventoResponse();
            response.setCorrelationId(request.getCorrelationId());
            response.setBody(new EventFetchResponse(
                    store.fetch(fetch.getContext(), fetch.getLastSequenceNumber(), fetch.getLimit())));
            response.setTimestamp(System.currentTimeMillis());
            return codec.encodeResponse(response);
        });
        lifecycle.registerLocalHandler(EventLastSequenceNumberRequest.class.getSimpleName(), payload -> {
            var request = codec.decodeRequest(payload);
            var response = new EventoResponse();
            response.setCorrelationId(request.getCorrelationId());
            response.setBody(new EventLastSequenceNumberResponse(store.lastSequenceNumber()));
            response.setTimestamp(System.currentTimeMillis());
            return codec.encodeResponse(response);
        });
        lifecycle.registerStraightPayloadType(EventFetchRequest.class.getSimpleName());
        lifecycle.registerStraightPayloadType(EventLastSequenceNumberRequest.class.getSimpleName());
        try {
            // No DataSource: the aggregate locks are JVM-local, which is all one broker needs.
            new CommandBrokerHandler(lifecycle, cluster, store, null);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
        this.port = lifecycle.start(port);
    }

    /** A broker on a free port. */
    public static EventoTestBroker start() {
        return new EventoTestBroker(0);
    }

    /** A broker on {@code port}, for example to restart one where bundles expect it. */
    public static EventoTestBroker start(int port) {
        return new EventoTestBroker(port);
    }

    /** The TCP port bundles connect to. */
    public int port() {
        return port;
    }

    /** The connection settings of a bundle that should use this broker. */
    public EventoServerMessageBusConfiguration busConfiguration() {
        return new EventoServerMessageBusConfiguration(new ClusterNodeAddress("127.0.0.1", port));
    }

    /** Every event stored, in sequence order. */
    public List<PublishedEvent> events() {
        return store.all();
    }

    /** Stores {@code event} of {@code aggregateId} as if a command had produced it, for consumers to read. */
    public long publish(DomainEvent event, String aggregateId) {
        return store.publishEvent(new DomainEventMessage(event), aggregateId);
    }

    /** The broker's routing, for assertions on connected bundles ({@code view()}, {@code availableView()}). */
    public BusLifecycle lifecycle() {
        return lifecycle;
    }

    /** The broker's events (bundles discovered, nodes left), to subscribe to before bundles connect. */
    public BusEventBus eventBus() {
        return eventBus;
    }

    @Override
    public void close() {
        lifecycle.stop(Duration.ofSeconds(5));
    }
}
