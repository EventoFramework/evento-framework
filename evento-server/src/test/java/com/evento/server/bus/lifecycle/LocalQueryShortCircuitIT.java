package com.evento.server.bus.lifecycle;

import com.evento.application.client.BundleClient;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
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

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A query the bundle can answer itself is answered without the broker once the bundle
 * is enabled, through the same handler path a remote query takes.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class LocalQueryShortCircuitIT {

    private static final String QUERY = "FindOrders";
    private static final String COMMAND = "PlaceOrder";

    private BusLifecycle lifecycle;
    private int port;

    @BeforeEach
    void setUp() {
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        lifecycle = new BusLifecycle(new NettyServerTransport(NettyTransportConfig.defaults()),
                connections, new ClusterRegistry(connections), new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, "broker-local", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                new JacksonCborPayloadCodec(), TokenValidator.acceptAll(),
                new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        port = lifecycle.start(0);
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(500));
    }

    private BundleClient ordersBundle(boolean shortCircuit) {
        return BundleClient.builder("orders", "orders-1")
                .host("127.0.0.1").port(port)
                .handlerPayloadTypes(List.of(QUERY, COMMAND))
                .registeredHandlers(List.of(
                        new RegisteredHandler(ComponentType.Projection, "OrderProjection",
                                HandlerType.QueryHandler, PayloadType.Query, QUERY, "Multiple", true, null),
                        new RegisteredHandler(ComponentType.Service, "OrderService",
                                HandlerType.CommandHandler, PayloadType.ServiceCommand, COMMAND, null, false, null)))
                .autoEnable(false)
                .localQueryShortCircuit(shortCircuit)
                .transportConfig(NettyTransportConfig.defaults())
                .build();
    }

    @Test
    void anOwnQueryIsAnsweredWithoutTheBrokerOnceTheBundleIsEnabled() throws Exception {
        try (var orders = ordersBundle(true)) {
            var invocations = new AtomicInteger();
            orders.registerRequestHandler(QUERY, (p, ctx) -> {
                invocations.incrementAndGet();
                return ("orders-for-" + new String(p)).getBytes();
            });
            orders.registerRequestHandler(COMMAND, (p, ctx) -> "placed".getBytes());
            orders.start().get(5, TimeUnit.SECONDS);

            // Not enabled yet (its projectors may still be catching up): it does not serve
            // itself, and the broker does not route to it either.
            assertThat(orders.answersLocally(QUERY)).isFalse();
            var beforeEnable = orders.request(QUERY, "c1".getBytes(), Duration.ofSeconds(5), true)
                    .get(5, TimeUnit.SECONDS);
            assertThat(beforeEnable.isError()).isTrue();
            assertThat(invocations).hasValue(0);

            orders.enable().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.isBundleAvailable("orders"));
            long forwardedBefore = lifecycle.forwardedRawCount();

            var answer = orders.request(QUERY, "c1".getBytes(), Duration.ofSeconds(5), true).get(5, TimeUnit.SECONDS);

            assertThat(answer.isError()).isFalse();
            assertThat(new String(answer.payload())).isEqualTo("orders-for-c1");
            assertThat(invocations).hasValue(1);
            assertThat(orders.localQueries()).isEqualTo(1);
            assertThat(lifecycle.forwardedRawCount()).isEqualTo(forwardedBefore);
            // Recorded like any round trip, under the query's payload type.
            assertThat(orders.latencies().requestRtt().snapshot().get(QUERY).count()).isEqualTo(1);

            // A command is never answered locally: it goes through the broker.
            var placed = orders.request(COMMAND, new byte[]{1}).get(5, TimeUnit.SECONDS);
            assertThat(new String(placed.payload())).isEqualTo("placed");
            assertThat(lifecycle.forwardedRawCount()).isGreaterThan(forwardedBefore);

            // Disabled again: the broker decides.
            orders.disable().get(5, TimeUnit.SECONDS);
            assertThat(orders.answersLocally(QUERY)).isFalse();
        }
    }

    @Test
    void aSlowLocalQueryTimesOutLikeARemoteOne() throws Exception {
        try (var orders = ordersBundle(true)) {
            orders.registerRequestHandler(QUERY, (p, ctx) -> {
                Thread.sleep(2_000);
                return new byte[0];
            });
            orders.registerRequestHandler(COMMAND, (p, ctx) -> new byte[0]);
            orders.start().get(5, TimeUnit.SECONDS);
            orders.enable().get(5, TimeUnit.SECONDS);

            var answer = orders.request(QUERY, new byte[0], Duration.ofMillis(200), true).get(5, TimeUnit.SECONDS);

            assertThat(answer.isError()).isTrue();
            assertThat(answer.error().exceptionClassName())
                    .isEqualTo(com.evento.transport.RequestTimeoutException.class.getName());
        }
    }

    @Test
    void withTheShortCircuitOffAnOwnQueryCrossesTheBroker() throws Exception {
        try (var orders = ordersBundle(false)) {
            orders.registerRequestHandler(QUERY, (p, ctx) -> "remote".getBytes());
            orders.registerRequestHandler(COMMAND, (p, ctx) -> new byte[0]);
            orders.start().get(5, TimeUnit.SECONDS);
            orders.enable().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.isBundleAvailable("orders"));
            long forwardedBefore = lifecycle.forwardedRawCount();

            var answer = orders.request(QUERY, new byte[0], Duration.ofSeconds(5), true).get(5, TimeUnit.SECONDS);

            assertThat(new String(answer.payload())).isEqualTo("remote");
            assertThat(orders.localQueries()).isZero();
            assertThat(lifecycle.forwardedRawCount()).isGreaterThan(forwardedBefore);
        }
    }
}
