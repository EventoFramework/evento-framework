package com.evento.server.bus.lifecycle;

import com.evento.application.client.BundleClient;
import com.evento.application.client.BundleClientState;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * A bundle that dials a name must also check who answered.
 *
 * <p>Real TCP on both ends, as every other bus test. The broker calls itself
 * {@code broker-alpha}; the bundle is told which broker it is allowed to
 * register with. The case this pins: two brokers reachable under one host name
 * on a shared network, where the transport cannot tell them apart and only the
 * {@code Welcome} can. A bundle that registers with the wrong one looks
 * healthy in its own logs while every command it handles is persisted somewhere
 * else — so the refusal has to happen at the handshake, before registration,
 * and the run loop has to keep dialing rather than give up.</p>
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class BundleClientIdentityIT {

    private static final String BROKER = "broker-alpha";

    private BusLifecycle lifecycle;
    private ClusterRegistry cluster;
    private int port;

    @BeforeEach
    void setUp() {
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        cluster = new ClusterRegistry(connections);
        lifecycle = new BusLifecycle(new NettyServerTransport(NettyTransportConfig.defaults()),
                connections, cluster, new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, BROKER,
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), new JacksonCborPayloadCodec(),
                TokenValidator.acceptAll(),
                new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        port = lifecycle.start(0);
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(500));
    }

    private BundleClient client(String expectedInstance) {
        return BundleClient.builder("shop", "shop-1")
                .host("127.0.0.1").port(port)
                .bundleVersion("1")
                .handlerPayloadTypes(List.of("com.evento.test.ShopCmd"))
                .expectedServerInstanceId(expectedInstance)
                .transportConfig(NettyTransportConfig.defaults())
                .build();
    }

    @Test
    void aBrokerThatAnnouncesAnotherIdentityIsRefusedBeforeRegistration() {
        var bundle = client("broker-beta");
        assertThatThrownBy(() -> bundle.start().get(3, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("announced instance 'broker-alpha'")
                .hasMessageContaining("expected 'broker-beta'");
        assertThat(bundle.state()).isNotEqualTo(BundleClientState.READY);
        // Nothing of the bundle reached the broker's routing table: the refusal
        // came before the registration notification, not after it.
        assertThat(cluster.knownPayloadTypes()).doesNotContain("com.evento.test.ShopCmd");
        bundle.close();
    }

    @Test
    void theBrokerItWasToldToExpectIsAccepted() throws Exception {
        try (var bundle = client(BROKER)) {
            bundle.start().get(5, TimeUnit.SECONDS);
            assertThat(bundle.state()).isEqualTo(BundleClientState.READY);
            await().atMost(Duration.ofSeconds(5))
                    .until(() -> cluster.knownPayloadTypes().contains("com.evento.test.ShopCmd"));
        }
    }

    /** Every bundle that existed before this check must go on connecting unchanged. */
    @Test
    void noExpectationAcceptsAnyBroker() throws Exception {
        try (var bundle = client(null)) {
            bundle.start().get(5, TimeUnit.SECONDS);
            assertThat(bundle.state()).isEqualTo(BundleClientState.READY);
        }
    }
}
