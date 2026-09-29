package com.evento.server.bus.lifecycle;

import com.evento.application.client.BundleClient;
import com.evento.application.client.BundleClientState;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.correlation.ForwardingDedupCache;
import com.evento.server.bus.event.BusEvent;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.bus.security.TokenValidator;
import com.evento.transport.BrokerChangedException;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.SendFailedException;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Fail-back to the preferred broker (N16), over real TCP on both ends.
 *
 * <p>Two brokers on ephemeral ports: A is endpoint 0 (the bundle's own cell),
 * B the fallback. "Killing" A is {@link BusLifecycle#stop}; "restarting" it is
 * a new {@link BusLifecycle} with the same instance id on the same port, which
 * is what a bundle sees when a server process is restarted. Windows are scaled
 * down from the 30 s default to keep the suite fast.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class BundleClientFailbackIT {

    private static final String CLUSTER = "evento-lab-cluster";
    private static final String SHOP_CMD = "com.evento.test.ShopCmd";
    private static final String SLOW = "com.evento.test.SlowCmd";

    private final List<Broker> brokers = new ArrayList<>();
    private final List<BundleClient> clients = new ArrayList<>();

    /** One broker: its lifecycle, registries, the port it listens on and every bus event it published. */
    private record Broker(String id, BusLifecycle lifecycle, ClusterRegistry cluster, int port,
                          List<BusEvent> events) {
        void kill() { lifecycle.stop(Duration.ofMillis(200)); }

        boolean has(String instanceId) {
            return lifecycle.view().stream().anyMatch(a -> a.instanceId().equals(instanceId));
        }

        List<BusEvent> eventsOf(String instanceId) {
            return events.stream().filter(e -> switch (e) {
                case BusEvent.NodeJoined j -> j.node().instanceId().equals(instanceId);
                case BusEvent.NodeLeft l -> l.node().instanceId().equals(instanceId);
                case BusEvent.NodeEnabled en -> en.node().instanceId().equals(instanceId);
                case BusEvent.NodeDisabled d -> d.node().instanceId().equals(instanceId);
                case BusEvent.BundleRegistered r -> r.node().instanceId().equals(instanceId);
                case BusEvent.BundleDiscovered d -> d.node().instanceId().equals(instanceId);
                default -> false;
            }).toList();
        }
    }

    private Broker brokerOn(String instanceId, int port) {
        var eventBus = new BusEventBus();
        var events = new CopyOnWriteArrayList<BusEvent>();
        eventBus.subscribe(events::add);
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var lifecycle = new BusLifecycle(new NettyServerTransport(NettyTransportConfig.defaults()),
                connections, cluster, new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, instanceId,
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), new JacksonCborPayloadCodec(),
                TokenValidator.acceptAll(),
                new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        lifecycle.setClusterName(CLUSTER);
        var b = new Broker(instanceId, lifecycle, cluster, lifecycle.start(port), events);
        brokers.add(b);
        return b;
    }

    private static NettyTransportConfig fastReconnect() {
        return NettyTransportConfig.defaults().withReconnectStrategy(new ExponentialBackoffWithJitter(
                Duration.ofMillis(50), Duration.ofMillis(200), 0.0, ExponentialBackoffWithJitter.UNBOUNDED));
    }

    /** A bundle preferring {@code ports[0]}, with a fast probe, the given window and no jitter. */
    private BundleClient.Builder client(String bundleId, String instanceId, Duration window, int... ports) {
        var b = BundleClient.builder(bundleId, instanceId)
                .bundleVersion("1")
                .transportConfig(fastReconnect())
                .defaultRequestTimeout(Duration.ofSeconds(30))
                .expectedClusterName(CLUSTER)
                .failbackProbeInterval(Duration.ofMillis(100))
                .failbackStabilityWindow(window)
                .failbackJitter(Duration.ZERO)
                .failbackDrainTimeout(Duration.ofSeconds(5));
        for (int p : ports) b.endpoint("127.0.0.1", p);
        return b;
    }

    private BundleClient track(BundleClient c) {
        clients.add(c);
        return c;
    }

    private static boolean readyOn(BundleClient c, String brokerId) {
        return c.state() == BundleClientState.READY && brokerId.equals(c.serverInstanceId());
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

    /** Bundle on A, A dies, bundle on B. Returns B. */
    private Broker failOverToB(BundleClient bundle, Broker a) throws Exception {
        var b = brokers.stream().filter(x -> x.id().equals("broker-b")).findFirst().orElseThrow();
        bundle.start().get(5, TimeUnit.SECONDS);
        assertThat(bundle.serverInstanceId()).isEqualTo("broker-a");
        a.kill();
        await().atMost(Duration.ofSeconds(10)).until(() -> readyOn(bundle, "broker-b"));
        return b;
    }

    @Test
    void afterThePreferredBrokerIsBackForTheWindowTheBundleMovesBackAndLeavesTheFallbackCleanly() throws Exception {
        var a = brokerOn("broker-a", 0);
        int aPort = a.port();
        brokerOn("broker-b", 0);
        var bundle = track(client("shop", "shop-1", Duration.ofMillis(1500), aPort,
                brokers.get(1).port()).handlerPayloadTypes(List.of(SHOP_CMD)).build());
        var b = failOverToB(bundle, a);
        await().atMost(Duration.ofSeconds(5)).until(() -> b.cluster().knownPayloadTypes().contains(SHOP_CMD));

        var restarted = brokerOn("broker-a", aPort);
        long restartedAt = System.nanoTime();

        await().atMost(Duration.ofSeconds(15)).until(() -> readyOn(bundle, "broker-a"));
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - restartedAt);
        // Not before the stability window: the first probe answered after the restart
        // starts it, so the move cannot come earlier than the window itself.
        assertThat(tookMs).isGreaterThanOrEqualTo(1500);
        assertThat(bundle.currentEndpoint().port()).isEqualTo(aPort);
        assertThat(bundle.failbacksCompleted()).isEqualTo(1);
        assertThat(bundle.failbacksFailed()).isZero();

        // Registered on A with its handlers...
        await().atMost(Duration.ofSeconds(5)).until(() -> restarted.cluster().knownPayloadTypes().contains(SHOP_CMD));
        // ...and gone from B: disabled first (out of the pick set while the drain ran),
        // then a normal leave, and its handlers out of B's routing table.
        await().atMost(Duration.ofSeconds(5)).until(() -> !b.has("shop-1"));
        assertThat(b.cluster().knownPayloadTypes()).doesNotContain(SHOP_CMD);
        var onB = b.eventsOf("shop-1").stream().map(e -> e.getClass().getSimpleName()).toList();
        assertThat(onB).containsSubsequence("NodeJoined", "NodeEnabled", "NodeDisabled", "NodeLeft");
        assertThat(b.eventsOf("shop-1").getLast())
                .isInstanceOfSatisfying(BusEvent.NodeLeft.class,
                        l -> assertThat(l.reason()).isEqualTo("transport_disconnected"));
    }

    @Test
    void probingThePreferredBrokerRegistersNothingThere() throws Exception {
        var a = brokerOn("broker-a", 0);
        int aPort = a.port();
        brokerOn("broker-b", 0);
        // A window far longer than the observation: only probes reach A.
        var bundle = track(client("shop", "shop-1", Duration.ofMinutes(5), aPort, brokers.get(1).port())
                .handlerPayloadTypes(List.of(SHOP_CMD)).build());
        failOverToB(bundle, a);

        var restarted = brokerOn("broker-a", aPort);
        long before = bundle.failbackProbesAnswered();
        await().atMost(Duration.ofSeconds(10)).until(() -> bundle.failbackProbesAnswered() >= before + 10);

        assertThat(restarted.lifecycle().view()).isEmpty();
        assertThat(restarted.events()).noneMatch(e -> e instanceof BusEvent.NodeJoined
                || e instanceof BusEvent.BundleRegistered || e instanceof BusEvent.BundleDiscovered
                || e instanceof BusEvent.NodeLeft);
        assertThat(restarted.cluster().knownPayloadTypes()).doesNotContain(SHOP_CMD);
        assertThat(restarted.lifecycle().zones()).isEmpty();
        assertThat(readyOn(bundle, "broker-b")).isTrue();
    }

    @Test
    void aFlappingPreferredBrokerNeverGetsTheBundleBack() throws Exception {
        var a = brokerOn("broker-a", 0);
        int aPort = a.port();
        brokerOn("broker-b", 0);
        var bundle = track(client("shop", "shop-1", Duration.ofMillis(1500), aPort, brokers.get(1).port())
                .handlerPayloadTypes(List.of(SHOP_CMD)).build());
        var b = failOverToB(bundle, a);

        // Up for less than the window, down briefly, again and again.
        for (int i = 0; i < 5; i++) {
            var flap = brokerOn("broker-a", aPort);
            Thread.sleep(900);
            flap.kill();
            Thread.sleep(300);
            assertThat(readyOn(bundle, "broker-b")).as("still on B after flap %d", i).isTrue();
        }
        assertThat(bundle.failbacksCompleted()).isZero();
        assertThat(bundle.failbacksFailed()).isZero();
        assertThat(b.has("shop-1")).isTrue();

        // Once it stays up, the bundle does go home.
        brokerOn("broker-a", aPort);
        await().atMost(Duration.ofSeconds(15)).until(() -> readyOn(bundle, "broker-a"));
        assertThat(bundle.failbacksCompleted()).isEqualTo(1);
    }

    @Test
    void aRequestInFlightWhenTheSwitchStartsCompletesBeforeTheBundleLeaves() throws Exception {
        var a = brokerOn("broker-a", 0);
        int aPort = a.port();
        var b = brokerOn("broker-b", 0);

        var invoked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var handler = track(BundleClient.builder("handler", "handler-1").bundleVersion("1")
                .transportConfig(fastReconnect()).endpoint("127.0.0.1", b.port())
                .handlerPayloadTypes(List.of(SLOW)).build());
        handler.registerRequestHandler(SLOW, (payload, ctx) -> {
            invoked.countDown();
            release.await(20, TimeUnit.SECONDS);
            return "done".getBytes();
        });
        handler.start().get(5, TimeUnit.SECONDS);

        var caller = track(client("caller", "caller-1", Duration.ofMillis(500), aPort, b.port()).build());
        failOverToB(caller, a);
        await().atMost(Duration.ofSeconds(5)).until(() -> b.lifecycle().availableView().size() == 2);

        var pending = caller.request(SLOW, "x".getBytes(), Duration.ofSeconds(30));
        assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();

        brokerOn("broker-a", aPort);
        // The switch has started once B has taken the caller out of its available view.
        await().atMost(Duration.ofSeconds(15)).until(() -> b.eventsOf("caller-1").stream()
                .anyMatch(e -> e instanceof BusEvent.NodeDisabled));

        // During the drain new work is refused as definitely-not-sent...
        var refused = caller.request(SHOP_CMD, "y".getBytes(), Duration.ofSeconds(5)).get(5, TimeUnit.SECONDS);
        assertThat(refused.isError()).isTrue();
        assertThat(refused.error().exceptionClassName()).isEqualTo(SendFailedException.class.getName());
        assertThat(refused.error().message()).contains("failing back");
        // ...and the caller has not left while its request is still open.
        assertThat(readyOn(caller, "broker-b")).isTrue();

        release.countDown();
        var response = pending.get(10, TimeUnit.SECONDS);
        assertThat(response.isError()).isFalse();
        assertThat(new String(response.payload())).isEqualTo("done");

        await().atMost(Duration.ofSeconds(10)).until(() -> readyOn(caller, "broker-a"));
        assertThat(caller.failbacksCompleted()).isEqualTo(1);
    }

    @Test
    void aRequestThatOutlivesTheDrainFailsWithBrokerChangedAndDoesNotHang() throws Exception {
        var a = brokerOn("broker-a", 0);
        int aPort = a.port();
        var b = brokerOn("broker-b", 0);

        var invoked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var handler = track(BundleClient.builder("handler", "handler-1").bundleVersion("1")
                .transportConfig(fastReconnect()).endpoint("127.0.0.1", b.port())
                .handlerPayloadTypes(List.of(SLOW)).build());
        handler.registerRequestHandler(SLOW, (payload, ctx) -> {
            invoked.countDown();
            release.await(30, TimeUnit.SECONDS);
            return "late".getBytes();
        });
        handler.start().get(5, TimeUnit.SECONDS);

        var caller = track(client("caller", "caller-1", Duration.ofMillis(500), aPort, b.port())
                .failbackDrainTimeout(Duration.ofMillis(300)).build());
        failOverToB(caller, a);
        await().atMost(Duration.ofSeconds(5)).until(() -> b.lifecycle().availableView().size() == 2);

        try {
            var pending = caller.request(SLOW, "x".getBytes(), Duration.ofSeconds(30));
            assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();
            long restartedAt = System.nanoTime();
            brokerOn("broker-a", aPort);

            var response = pending.get(15, TimeUnit.SECONDS);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - restartedAt);
            assertThat(response.isError()).isTrue();
            assertThat(response.error().exceptionClassName()).isEqualTo(BrokerChangedException.class.getName());
            assertThat(response.error().message()).contains("broker-b").contains("broker-a");
            assertThat(elapsedMs).isLessThan(15_000);
            // Failed at A's Welcome, i.e. just before the caller registers there.
            await().atMost(Duration.ofSeconds(5)).until(() -> readyOn(caller, "broker-a"));
            assertThat(caller.pendingOutboundCount()).isZero();
        } finally {
            release.countDown();
        }
    }

    @Test
    void aPreferredBrokerThatFailsTheIdentityCheckSendsTheBundleBackAndBacksOff() throws Exception {
        var a = brokerOn("broker-a", 0);
        int aPort = a.port();
        brokerOn("broker-b", 0);
        var bundle = track(client("shop", "shop-1", Duration.ofMillis(800), aPort, brokers.get(1).port())
                .handlerPayloadTypes(List.of(SHOP_CMD)).build());
        failOverToB(bundle, a);

        // Something else now answers on A's address: healthy to a probe, which cannot
        // see identity, but refused by the real handshake.
        var foreign = brokerOn("broker-x", aPort);
        foreign.lifecycle().setClusterName("some-other-cluster");

        await().atMost(Duration.ofSeconds(10)).until(() -> bundle.failbacksFailed() == 1);
        await().atMost(Duration.ofSeconds(5)).until(() -> readyOn(bundle, "broker-b"));
        assertThat(foreign.cluster().knownPayloadTypes()).doesNotContain(SHOP_CMD);
        await().atMost(Duration.ofSeconds(5)).until(() -> foreign.lifecycle().view().isEmpty());
        // The next attempt waits twice the window (1.6 s), not one.
        Thread.sleep(1200);
        assertThat(bundle.failbacksFailed()).isEqualTo(1);
        assertThat(bundle.failbacksCompleted()).isZero();
    }

    @Test
    void withFailbackDisabledTheBundleStaysOnTheFallback() throws Exception {
        var a = brokerOn("broker-a", 0);
        int aPort = a.port();
        brokerOn("broker-b", 0);
        var bundle = track(client("shop", "shop-1", Duration.ofMillis(200), aPort, brokers.get(1).port())
                .failbackEnabled(false).build());
        failOverToB(bundle, a);

        brokerOn("broker-a", aPort);
        Thread.sleep(2000);
        assertThat(readyOn(bundle, "broker-b")).isTrue();
        assertThat(bundle.failbackProbesAnswered()).isZero();
    }
}
