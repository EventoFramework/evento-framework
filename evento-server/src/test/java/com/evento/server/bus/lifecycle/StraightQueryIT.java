package com.evento.server.bus.lifecycle;

import com.evento.application.client.BundleClient;
import com.evento.application.client.BundleClientState;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Queries are routed "straight" by the broker: declared idempotent in the bundle's
 * registration, they claim no exactly-once slot, their responses are never retained
 * for replay nor buffered for a caller that went away, and a caller that loses one
 * with its session simply asks it again on the next.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class StraightQueryIT {

    private static final String QUERY = "FindOrders";
    private static final String COMMAND = "PlaceOrder";

    private BusLifecycle lifecycle;
    private ForwardingDedupCache dedup;
    private int port;

    @BeforeEach
    void setUp() {
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        dedup = new ForwardingDedupCache(1000, Duration.ofMinutes(1));
        lifecycle = new BusLifecycle(new NettyServerTransport(NettyTransportConfig.defaults()),
                connections, cluster, new CorrelationStore(Duration.ofMillis(100)), new ForwardingTable(),
                eventBus, "broker-straight", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                new JacksonCborPayloadCodec(), TokenValidator.acceptAll(), dedup);
        port = lifecycle.start(0);
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(500));
    }

    private BundleClient handlerBundle() {
        return BundleClient.builder("orders", "orders-1")
                .host("127.0.0.1").port(port)
                .bundleVersion("1")
                .handlerPayloadTypes(List.of(QUERY, COMMAND))
                .registeredHandlers(List.of(
                        new RegisteredHandler(ComponentType.Projection, "OrderProjection",
                                HandlerType.QueryHandler, PayloadType.Query, QUERY, "Multiple", true, null),
                        new RegisteredHandler(ComponentType.Service, "OrderService",
                                HandlerType.CommandHandler, PayloadType.ServiceCommand, COMMAND, null, false, null)))
                .transportConfig(NettyTransportConfig.defaults())
                .build();
    }

    private BundleClient callerBundle() {
        return BundleClient.builder("web", "web-1")
                .host("127.0.0.1").port(port)
                .bundleVersion("1")
                .handlerPayloadTypes(List.of())
                .transportConfig(NettyTransportConfig.defaults())
                .defaultRequestTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Test
    void theBrokerRetainsNoQueryResponseButStillDeduplicatesCommands() throws Exception {
        try (var handler = handlerBundle(); var caller = callerBundle()) {
            handler.registerRequestHandler(QUERY, (p, ctx) -> new byte[512 * 1024]);
            handler.registerRequestHandler(COMMAND, (p, ctx) -> "done".getBytes());
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);
            assertThat(lifecycle.isStraight(QUERY)).isTrue();
            assertThat(lifecycle.isStraight(COMMAND)).isFalse();

            for (int i = 0; i < 5; i++) {
                var answer = caller.request(QUERY, new byte[]{1}, Duration.ofSeconds(5), true).get(5, TimeUnit.SECONDS);
                assertThat(answer.isError()).isFalse();
                assertThat(answer.payload()).hasSize(512 * 1024);
            }
            assertThat(dedup.size()).isZero();
            assertThat(dedup.retainedBytes()).isZero();

            var done = caller.request(COMMAND, new byte[]{2}).get(5, TimeUnit.SECONDS);
            assertThat(done.isError()).isFalse();
            assertThat(dedup.size()).isEqualTo(1);
            assertThat(dedup.retainedBytes()).isPositive();
        }
    }

    @Test
    void aQueryLostWithTheCallersSessionIsAskedAgainOnTheNextOne() throws Exception {
        try (var handler = handlerBundle(); var caller = callerBundle()) {
            var invocations = new AtomicInteger();
            var firstArrived = new CountDownLatch(1);
            var releaseFirst = new CountDownLatch(1);
            handler.registerRequestHandler(QUERY, (p, ctx) -> {
                if (invocations.incrementAndGet() == 1) {
                    firstArrived.countDown();
                    releaseFirst.await(10, TimeUnit.SECONDS);
                    return "stale".getBytes();
                }
                return "fresh".getBytes();
            });
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            var answer = caller.request(QUERY, new byte[]{1}, Duration.ofSeconds(20), true);
            assertThat(firstArrived.await(5, TimeUnit.SECONDS)).isTrue();

            // The caller loses its session while the first execution runs; that execution's
            // answer reaches a broker with no caller to give it to and is dropped.
            caller.currentTransportForTest().close();
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.view().size() == 1);
            releaseFirst.countDown();
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.reconnectBufferBytes() == 0
                    && invocations.get() >= 1);

            await().atMost(15, TimeUnit.SECONDS).until(() -> caller.state() == BundleClientState.READY);
            var response = answer.get(10, TimeUnit.SECONDS);
            assertThat(response.isError()).isFalse();
            assertThat(new String(response.payload())).isEqualTo("fresh");
            assertThat(invocations.get()).isEqualTo(2);
            assertThat(lifecycle.reconnectBufferBytes()).isZero();
        }
    }

    @Test
    void aCommandResponseOverTheReconnectBufferBoundIsNotBuffered() throws Exception {
        lifecycle.setReconnectBufferMaxBytes(64 * 1024);
        try (var handler = handlerBundle(); var caller = callerBundle()) {
            var arrived = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var replied = new CountDownLatch(1);
            handler.registerRequestHandler(COMMAND, (p, ctx) -> {
                arrived.countDown();
                release.await(10, TimeUnit.SECONDS);
                replied.countDown();
                var incompressible = new byte[256 * 1024];
                new java.util.Random(7).nextBytes(incompressible);
                return incompressible;
            });
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            var answer = caller.request(COMMAND, new byte[]{1}, Duration.ofSeconds(4));
            assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue();
            caller.currentTransportForTest().close();
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.view().size() == 1);
            release.countDown();
            assertThat(replied.await(5, TimeUnit.SECONDS)).isTrue();

            // Nothing was buffered: the command is not asked again (it has side effects),
            // so its caller ends indeterminate at its own timeout.
            await().during(300, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                    .until(() -> lifecycle.reconnectBufferBytes() == 0);
            var response = answer.get(10, TimeUnit.SECONDS);
            assertThat(response.isError()).isTrue();
            assertThat(response.error().exceptionClassName())
                    .isEqualTo(com.evento.transport.RequestTimeoutException.class.getName());
        }
    }
}
