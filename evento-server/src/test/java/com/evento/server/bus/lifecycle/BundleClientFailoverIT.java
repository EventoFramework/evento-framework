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
import com.evento.transport.BrokerChangedException;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.message.Response;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * A bundle holding several broker endpoints fails over between them, and does
 * not leave its callers waiting on replies that cannot follow it.
 *
 * <p>Two (or three) real brokers on ephemeral ports, real TCP on both ends.
 * "Killing" a broker is {@link BusLifecycle#stop}: its server socket and every
 * accepted connection close, which is what a bundle sees when the process dies
 * with the kernel still answering (RST / refused on reconnect).
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class BundleClientFailoverIT {

    private static final String CLUSTER = "evento-lab-cluster";
    private static final String SLOW = "com.evento.test.SlowCmd";

    private final List<Broker> brokers = new ArrayList<>();
    private final List<BundleClient> clients = new ArrayList<>();

    /** One broker: its lifecycle, registry and the port it listens on. */
    private record Broker(String id, BusLifecycle lifecycle, ClusterRegistry cluster, int port) {
        void kill() { lifecycle.stop(Duration.ofMillis(200)); }
    }

    private Broker broker(String instanceId, String clusterName) {
        return brokerOn(instanceId, clusterName, 0);
    }

    private Broker brokerOn(String instanceId, String clusterName, int port) {
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var lifecycle = new BusLifecycle(new NettyServerTransport(NettyTransportConfig.defaults()),
                connections, cluster, new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, instanceId,
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), new JacksonCborPayloadCodec(),
                TokenValidator.acceptAll(),
                new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        lifecycle.setClusterName(clusterName);
        var b = new Broker(instanceId, lifecycle, cluster, lifecycle.start(port));
        brokers.add(b);
        return b;
    }

    /** Fast, jitter-free backoff so failover is measured in milliseconds, not in the 0.5–30 s default. */
    private static NettyTransportConfig fastReconnect() {
        return NettyTransportConfig.defaults().withReconnectStrategy(new ExponentialBackoffWithJitter(
                Duration.ofMillis(50), Duration.ofMillis(200), 0.0, ExponentialBackoffWithJitter.UNBOUNDED));
    }

    private BundleClient.Builder client(String bundleId, String instanceId, int... ports) {
        var b = BundleClient.builder(bundleId, instanceId)
                .bundleVersion("1")
                .transportConfig(fastReconnect())
                .defaultRequestTimeout(Duration.ofSeconds(30));
        for (int p : ports) b.endpoint("127.0.0.1", p);
        return b;
    }

    private BundleClient track(BundleClient c) {
        clients.add(c);
        return c;
    }

    private static int closedPort() throws Exception {
        try (var s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @AfterEach
    void tearDown() {
        for (var c : clients) {
            try { c.stop(Duration.ofMillis(200)); } catch (Throwable ignored) {}
        }
        for (var b : brokers) {
            try { b.kill(); } catch (Throwable ignored) {}
        }
    }

    // ---------- G2: endpoint rotation ----------

    @Test
    void aBundleWhoseBrokerDiesBecomesReadyOnTheNextEndpoint() throws Exception {
        var a = broker("broker-a", CLUSTER);
        var b = broker("broker-b", CLUSTER);
        var bundle = track(client("shop", "shop-1", a.port(), b.port())
                .handlerPayloadTypes(List.of("com.evento.test.ShopCmd"))
                .build());

        bundle.start().get(5, TimeUnit.SECONDS);
        assertThat(bundle.serverInstanceId()).isEqualTo("broker-a");
        assertThat(bundle.currentEndpoint().port()).isEqualTo(a.port());

        a.kill();

        await().atMost(Duration.ofSeconds(10)).until(() ->
                bundle.state() == BundleClientState.READY && "broker-b".equals(bundle.serverInstanceId()));
        assertThat(bundle.currentEndpoint().port()).isEqualTo(b.port());
        // Registered on the survivor: its routing table knows the bundle's handlers.
        await().atMost(Duration.ofSeconds(5))
                .until(() -> b.cluster().knownPayloadTypes().contains("com.evento.test.ShopCmd"));
    }

    @Test
    void aPreferredBrokerThatIsDownAtBootDoesNotFailStartWhenAnotherEndpointAnswers() throws Exception {
        var b = broker("broker-b", CLUSTER);
        var bundle = track(client("shop", "shop-1", closedPort(), b.port()).build());

        bundle.start().get(10, TimeUnit.SECONDS);

        assertThat(bundle.state()).isEqualTo(BundleClientState.READY);
        assertThat(bundle.serverInstanceId()).isEqualTo("broker-b");
    }

    @Test
    void startFailsOnlyAfterEveryEndpointWasTried() throws Exception {
        var bundle = track(client("shop", "shop-1", closedPort(), closedPort()).build());
        var connectAttempts = new AtomicInteger();
        bundle.onStateChange((from, to) -> {
            if (to == BundleClientState.CONNECTING) connectAttempts.incrementAndGet();
        });

        assertThatThrownBy(() -> bundle.start().get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        // start() -> CONNECTING is the first transition; one per dial after that.
        assertThat(connectAttempts.get()).isGreaterThanOrEqualTo(2);
        assertThat(bundle.state()).isNotEqualTo(BundleClientState.CLOSED);
    }

    @Test
    void afterADropTheBundleFailsBackToThePreferredBroker() throws Exception {
        var local = broker("broker-local", CLUSTER);
        var remote = broker("broker-remote", CLUSTER);
        int localPort = local.port();
        var bundle = track(client("shop", "shop-1", localPort, remote.port()).build());
        bundle.start().get(5, TimeUnit.SECONDS);
        assertThat(bundle.serverInstanceId()).isEqualTo("broker-local");

        local.kill();
        await().atMost(Duration.ofSeconds(10)).until(() ->
                bundle.state() == BundleClientState.READY && "broker-remote".equals(bundle.serverInstanceId()));

        // The local broker comes back on its old port. Within the fail-back stability
        // window (30 s by default, BundleClientFailbackIT) nothing moves the bundle...
        var restarted = brokerOn("broker-local-2", CLUSTER, localPort);
        Thread.sleep(500);
        assertThat(bundle.serverInstanceId()).isEqualTo("broker-remote");

        // ...but the next disconnect dials endpoint 0 first again.
        bundle.currentTransportForTest().close();
        await().atMost(Duration.ofSeconds(10)).until(() ->
                bundle.state() == BundleClientState.READY && restarted.id().equals(bundle.serverInstanceId()));
    }

    // ---------- G4: in-flight requests on a broker change ----------

    @Test
    void aPendingRequestFailsFastWithBrokerChangedWhenTheBundleLandsOnAnotherBroker() throws Exception {
        var a = broker("broker-a", CLUSTER);
        var b = broker("broker-b", CLUSTER);

        var invoked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var handler = track(client("handler", "handler-1", a.port())
                .handlerPayloadTypes(List.of(SLOW)).build());
        handler.registerRequestHandler(SLOW, (payload, ctx) -> {
            invoked.countDown();
            release.await(20, TimeUnit.SECONDS);
            return new byte[0];
        });
        handler.start().get(5, TimeUnit.SECONDS);

        var caller = track(client("caller", "caller-1", a.port(), b.port()).build());
        caller.start().get(5, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> a.lifecycle().availableView().size() == 2);

        CompletableFuture<Response> pending = caller.request(SLOW, "x".getBytes(), Duration.ofSeconds(30));
        assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();

        long killedAt = System.nanoTime();
        a.kill();
        try {
            var response = pending.get(10, TimeUnit.SECONDS);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - killedAt);

            assertThat(response.isError()).isTrue();
            assertThat(response.error().exceptionClassName()).isEqualTo(BrokerChangedException.class.getName());
            assertThat(response.error().message())
                    .contains("indeterminate").contains("broker-a").contains("broker-b");
            // Far inside the 30 s request timeout: the failure came from the new Welcome.
            assertThat(elapsedMs).isLessThan(10_000);
            assertThat(caller.serverInstanceId()).isEqualTo("broker-b");
            assertThat(caller.pendingOutboundCount()).isZero();
        } finally {
            release.countDown();
        }
    }

    @Test
    void aPendingRequestSurvivesAReconnectToTheSameBroker() throws Exception {
        var a = broker("broker-a", CLUSTER);

        var invoked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var handler = track(client("handler", "handler-1", a.port())
                .handlerPayloadTypes(List.of(SLOW)).build());
        handler.registerRequestHandler(SLOW, (payload, ctx) -> {
            invoked.countDown();
            release.await(20, TimeUnit.SECONDS);
            return "done".getBytes();
        });
        handler.start().get(5, TimeUnit.SECONDS);

        var caller = track(client("caller", "caller-1", a.port()).build());
        caller.start().get(5, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> a.lifecycle().availableView().size() == 2);

        var pending = caller.request(SLOW, "x".getBytes(), Duration.ofSeconds(30));
        assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();

        // Drop only the caller's link; it comes back to the same broker.
        caller.currentTransportForTest().close();
        await().atMost(Duration.ofSeconds(10)).until(() -> caller.state() == BundleClientState.READY
                && caller.currentTransportForTest() != null);
        assertThat(pending).isNotDone();

        release.countDown();
        var response = pending.get(10, TimeUnit.SECONDS);
        assertThat(response.isError()).isFalse();
        assertThat(new String(response.payload())).isEqualTo("done");
    }

    // ---------- G13: cluster identity ----------

    @Test
    void aBrokerOfAnotherClusterIsSkippedAndTheNextEndpointIsUsed() throws Exception {
        var foreign = broker("broker-x", "some-other-cluster");
        var b = broker("broker-b", CLUSTER);
        var bundle = track(client("shop", "shop-1", foreign.port(), b.port())
                .expectedClusterName(CLUSTER)
                .handlerPayloadTypes(List.of("com.evento.test.ShopCmd"))
                .build());

        bundle.start().get(10, TimeUnit.SECONDS);

        assertThat(bundle.serverInstanceId()).isEqualTo("broker-b");
        // Nothing reached the foreign broker's routing table: refused before registration.
        assertThat(foreign.cluster().knownPayloadTypes()).doesNotContain("com.evento.test.ShopCmd");
    }

    @Test
    void aClusterNameMismatchIsRefusedAndTheBundleKeepsDialing() throws Exception {
        var foreign = broker("broker-x", "some-other-cluster");
        var bundle = track(client("shop", "shop-1", foreign.port())
                .expectedClusterName(CLUSTER)
                .handlerPayloadTypes(List.of("com.evento.test.ShopCmd"))
                .build());
        var handshakes = new AtomicInteger();
        bundle.onStateChange((from, to) -> {
            if (to == BundleClientState.HANDSHAKING) handshakes.incrementAndGet();
        });

        assertThatThrownBy(() -> bundle.start().get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("announced cluster 'some-other-cluster'")
                .hasMessageContaining("expected '" + CLUSTER + "'");

        await().atMost(Duration.ofSeconds(10)).until(() -> handshakes.get() >= 3);
        assertThat(bundle.state()).isNotIn(BundleClientState.READY, BundleClientState.CLOSED);
        assertThat(foreign.cluster().knownPayloadTypes()).doesNotContain("com.evento.test.ShopCmd");
    }

    @Test
    void aBrokerThatAnnouncesNoClusterNameCannotProveMembership() throws Exception {
        var anonymous = broker("broker-anon", null);
        var bundle = track(client("shop", "shop-1", anonymous.port())
                .expectedClusterName(CLUSTER).build());

        assertThatThrownBy(() -> bundle.start().get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("announced cluster ''");
    }

    @Test
    void withoutAnExpectedClusterNameAnyBrokerIsAccepted() throws Exception {
        var anonymous = broker("broker-anon", null);
        var bundle = track(client("shop", "shop-1", anonymous.port()).build());

        bundle.start().get(5, TimeUnit.SECONDS);

        assertThat(bundle.state()).isEqualTo(BundleClientState.READY);
    }
}
