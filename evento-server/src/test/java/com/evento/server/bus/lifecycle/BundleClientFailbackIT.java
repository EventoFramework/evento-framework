package com.evento.server.bus.lifecycle;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.evento.application.client.BundleClient;
import com.evento.application.client.BundleClientState;
import com.evento.application.client.EventoServerAdapter;
import com.evento.application.client.connection.ConnectionSupervisor;
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
import com.evento.transport.RequestNotSentException;
import com.evento.transport.SendFailedException;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private static final String BIG = "com.evento.test.BigQuery";

    private final List<Broker> brokers = new ArrayList<>();
    private final List<BundleClient> clients = new ArrayList<>();

    /** One broker: its lifecycle, registries, the port it listens on and every bus event it published. */
    private record Broker(String id, BusLifecycle lifecycle, ClusterRegistry cluster, int port,
                          List<BusEvent> events) {
        void kill() { lifecycle.stop(Duration.ofMillis(200)); }

        boolean has(String instanceId) {
            return lifecycle.view().stream().anyMatch(a -> a.instanceId().equals(instanceId));
        }

        boolean available(String instanceId) {
            return lifecycle.availableView().stream().anyMatch(a -> a.instanceId().equals(instanceId));
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
        return brokerOn(instanceId, port, CLUSTER, null);
    }

    /** A broker announcing {@code cluster}, whose probe readiness is {@code readiness} (null: always ready). */
    private Broker brokerOn(String instanceId, int port, String cluster, BooleanSupplier readiness) {
        var eventBus = new BusEventBus();
        var events = new CopyOnWriteArrayList<BusEvent>();
        eventBus.subscribe(events::add);
        var connections = new ConnectionRegistry(eventBus);
        var cluster0 = new ClusterRegistry(connections);
        var lifecycle = new BusLifecycle(new NettyServerTransport(NettyTransportConfig.defaults()),
                connections, cluster0, new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, instanceId,
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), new JacksonCborPayloadCodec(),
                TokenValidator.acceptAll(),
                new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        // Before start(): what the first Welcome announces.
        lifecycle.setClusterName(cluster);
        if (readiness != null) lifecycle.setProbeReadiness(readiness);
        var b = new Broker(instanceId, lifecycle, cluster0, lifecycle.start(port), events);
        brokers.add(b);
        return b;
    }

    /** A port where broker A was listening and is not any more (the bundle's preferred broker is down). */
    private int deadPreferredPort() {
        var a = brokerOn("broker-a", 0);
        a.kill();
        return a.port();
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

    /** A plain bundle attached to one broker only (never fails back). */
    private BundleClient.Builder plain(String bundleId, String instanceId, int port) {
        return BundleClient.builder(bundleId, instanceId).bundleVersion("1")
                .transportConfig(fastReconnect()).endpoint("127.0.0.1", port);
    }

    private BundleClient track(BundleClient c) {
        clients.add(c);
        return c;
    }

    private static boolean readyOn(BundleClient c, String brokerId) {
        return c.state() == BundleClientState.READY && brokerId.equals(c.serverInstanceId());
    }

    private static boolean probeThreadAlive(String instanceId) {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.isAlive() && t.getName().equals("evento-failback-probe-" + instanceId));
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
        assertThat(bundle.reactiveFailbacks()).isZero();

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
        var handler = track(plain("handler", "handler-1", b.port())
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
        // ...and surfaces to the v1 gateways as a typed not-sent failure (LOW-8), still an
        // IllegalStateException as before, for code that catches that.
        var adapter = new EventoServerAdapter(caller, "caller", "caller-1", 1L);
        assertThatThrownBy(() -> adapter.request("y", 5, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(RequestNotSentException.class)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failing back");
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
        var handler = track(plain("handler", "handler-1", b.port())
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
    void aPreferredBrokerThatFailsTheIdentityCheckSendsTheBundleBackAndDoublesTheWindow() throws Exception {
        var a = brokerOn("broker-a", 0);
        int aPort = a.port();
        brokerOn("broker-b", 0);
        var bundle = track(client("shop", "shop-1", Duration.ofMillis(800), aPort, brokers.get(1).port())
                .handlerPayloadTypes(List.of(SHOP_CMD)).build());
        failOverToB(bundle, a);

        // Something else now answers on A's address: healthy to a probe, which cannot
        // see identity, but refused by the real handshake (its cluster is set before
        // it starts, so every Welcome it ever sends announces the wrong one).
        var foreign = brokerOn("broker-x", aPort, "some-other-cluster", null);

        await().atMost(Duration.ofSeconds(10)).until(() -> bundle.failbacksFailed() == 1);
        long firstFailure = System.nanoTime();
        await().atMost(Duration.ofSeconds(5)).until(() -> readyOn(bundle, "broker-b"));
        assertThat(foreign.cluster().knownPayloadTypes()).doesNotContain(SHOP_CMD);
        await().atMost(Duration.ofSeconds(5)).until(() -> foreign.lifecycle().view().isEmpty());

        // The second attempt does come, after twice the window (1.6 s), not one.
        await().atMost(Duration.ofSeconds(10)).until(() -> bundle.failbacksFailed() == 2);
        long gapMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - firstFailure);
        assertThat(gapMs).isGreaterThanOrEqualTo(1600);
        assertThat(bundle.failbacksCompleted()).isZero();
        await().atMost(Duration.ofSeconds(5)).until(() -> readyOn(bundle, "broker-b"));
    }

    @Test
    void aPreferredBrokerThatKeepsRefusingTheBundleSuspendsFailbackUntilASessionThereSucceeds() throws Exception {
        var a = brokerOn("broker-a", 0);
        int aPort = a.port();
        var b = brokerOn("broker-b", 0);
        int bPort = b.port();
        var bundle = track(client("shop", "shop-1", Duration.ofMillis(200), aPort, bPort)
                .handlerPayloadTypes(List.of(SHOP_CMD)).build());
        failOverToB(bundle, a);

        var foreign = brokerOn("broker-x", aPort, "some-other-cluster", null);
        // 200 + 400 + 800 ms of windows, then no more switches: one forced outage per
        // attempt is the cost of a switch that cannot succeed.
        await().atMost(Duration.ofSeconds(15)).until(bundle::failbackSuspended);
        assertThat(bundle.failbacksFailed()).isEqualTo(ConnectionSupervisor.MAX_FAILBACK_REFUSALS);
        await().atMost(Duration.ofSeconds(5)).until(() -> readyOn(bundle, "broker-b"));
        long probes = bundle.failbackProbesAnswered();
        // A fourth attempt would come after 1.6 s; wait longer than that.
        Thread.sleep(2_500);
        assertThat(bundle.failbacksFailed()).isEqualTo(ConnectionSupervisor.MAX_FAILBACK_REFUSALS);
        assertThat(bundle.failbackProbesAnswered()).as("no probing while suspended").isEqualTo(probes);
        assertThat(readyOn(bundle, "broker-b")).isTrue();

        // The genuine broker is back on A, but a suspended bundle does not go there by itself...
        foreign.kill();
        var genuine = brokerOn("broker-a", aPort);
        Thread.sleep(1_000);
        assertThat(readyOn(bundle, "broker-b")).isTrue();
        // ...until an ordinary reconnect lands it on A: that clears the suspension and
        // the back-off (READY on endpoint 0, whatever brought it there).
        b.kill();
        await().atMost(Duration.ofSeconds(10)).until(() -> readyOn(bundle, "broker-a"));
        assertThat(bundle.failbackSuspended()).isFalse();

        // Fail-back works again, with the base window: not 16x of it.
        brokerOn("broker-b", bPort);
        genuine.kill();
        await().atMost(Duration.ofSeconds(10)).until(() -> readyOn(bundle, "broker-b"));
        brokerOn("broker-a", aPort);
        long restartedAt = System.nanoTime();
        await().atMost(Duration.ofSeconds(10)).until(() -> readyOn(bundle, "broker-a"));
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - restartedAt)).isLessThan(1_600);
        assertThat(bundle.failbacksCompleted()).isEqualTo(1);
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

    // ---- review fixes ----

    /**
     * HIGH-2: in an HA pair the fallback has no handler of its own. Once the cell's
     * handler is home, every request a caller still on the fallback sends fails with
     * "no handler". The caller must not sit out its window (5 minutes here): the
     * first such reply moves it back, and the application's retry succeeds.
     */
    @Test
    void aCallerThatFindsNoHandlerOnTheFallbackMovesBackAtOnce() throws Exception {
        var a = brokerOn("broker-a", 0);
        int aPort = a.port();
        var b = brokerOn("broker-b", 0);
        var handler = track(client("handler", "handler-1", Duration.ofMillis(300), aPort, b.port())
                .handlerPayloadTypes(List.of(SHOP_CMD)).build());
        handler.registerRequestHandler(SHOP_CMD, (payload, ctx) -> "ok".getBytes());
        var caller = track(client("caller", "caller-1", Duration.ofMinutes(5), aPort, b.port()).build());
        handler.start().get(5, TimeUnit.SECONDS);
        caller.start().get(5, TimeUnit.SECONDS);
        a.kill();
        await().atMost(Duration.ofSeconds(10)).until(() -> readyOn(handler, "broker-b") && readyOn(caller, "broker-b"));
        await().atMost(Duration.ofSeconds(5)).until(() -> b.available("handler-1"));
        assertThat(caller.request(SHOP_CMD, "x".getBytes()).get(5, TimeUnit.SECONDS).isError()).isFalse();

        brokerOn("broker-a", aPort);
        await().atMost(Duration.ofSeconds(10)).until(() -> readyOn(handler, "broker-a"));
        await().atMost(Duration.ofSeconds(5)).until(() -> !b.has("handler-1"));
        assertThat(readyOn(caller, "broker-b")).as("the caller's own window is far off").isTrue();

        long start = System.nanoTime();
        var miss = caller.request(SHOP_CMD, "x".getBytes()).get(5, TimeUnit.SECONDS);
        assertThat(miss.isError()).isTrue();
        assertThat(miss.error().message()).startsWith("no handler for");
        // What an application does with a failed command: retry it.
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).until(() -> {
            var r = caller.request(SHOP_CMD, "x".getBytes()).get(5, TimeUnit.SECONDS);
            return !r.isError() && "ok".equals(new String(r.payload()));
        });
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertThat(tookMs).isLessThan(5_000);
        assertThat(readyOn(caller, "broker-a")).isTrue();
        assertThat(caller.reactiveFailbacks()).isEqualTo(1);
        assertThat(caller.failbacksCompleted()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).until(() -> !b.has("caller-1"));
    }

    /**
     * MEDIUM-6: a broker whose port is bound but whose application is not ready
     * yet (the Spring server binds the bus long before JPA and the event store are
     * up) must not look healthy to a probe.
     */
    @Test
    void aPreferredBrokerThatIsBoundButNotReadyDoesNotGetTheBundleBack() throws Exception {
        var a = brokerOn("broker-a", 0);
        int aPort = a.port();
        brokerOn("broker-b", 0);
        var bundle = track(client("shop", "shop-1", Duration.ofMillis(300), aPort, brokers.get(1).port())
                .handlerPayloadTypes(List.of(SHOP_CMD)).build());
        failOverToB(bundle, a);

        var ready = new AtomicBoolean(false);
        var restarted = brokerOn("broker-a", aPort, CLUSTER, ready::get);
        long probes = bundle.failbackProbesAnswered();
        Thread.sleep(2_000);  // many windows' worth of probes
        assertThat(readyOn(bundle, "broker-b")).isTrue();
        assertThat(bundle.failbackProbesAnswered()).isEqualTo(probes);
        assertThat(restarted.lifecycle().view()).isEmpty();

        ready.set(true);
        await().atMost(Duration.ofSeconds(10)).until(() -> readyOn(bundle, "broker-a"));
        assertThat(bundle.failbacksCompleted()).isEqualTo(1);
    }

    /**
     * HIGH-1: the application enables the bundle (its projectors have caught up) in
     * the middle of a fail-back drain. That must neither throw — EventoBundle's
     * startup used to die on it, with sagas and observers never started — nor
     * re-enable the broker being left; the enable is sent on the preferred broker.
     * Notifications (consumer registration, stats) still go out during the drain.
     */
    @Test
    void enablingDuringTheDrainIsDeferredToThePreferredBroker() throws Exception {
        int aPort = deadPreferredPort();
        var b = brokerOn("broker-b", 0);
        var invoked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var slow = track(plain("slow", "slow-1", b.port()).handlerPayloadTypes(List.of(SLOW)).build());
        slow.registerRequestHandler(SLOW, (payload, ctx) -> {
            invoked.countDown();
            release.await(30, TimeUnit.SECONDS);
            return "done".getBytes();
        });
        slow.start().get(5, TimeUnit.SECONDS);

        // Like EventoBundle: registered, not enabled until its projectors have caught up.
        var bundle = track(client("shop", "shop-1", Duration.ofMillis(300), aPort, b.port())
                .handlerPayloadTypes(List.of(SHOP_CMD)).autoEnable(false).build());
        bundle.start().get(10, TimeUnit.SECONDS);
        assertThat(readyOn(bundle, "broker-b")).isTrue();
        assertThat(b.available("shop-1")).isFalse();

        try {
            // An outbound request holds the drain open.
            var pending = bundle.request(SLOW, "x".getBytes(), Duration.ofSeconds(30));
            assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();
            var a = brokerOn("broker-a", aPort);
            await().atMost(Duration.ofSeconds(10)).until(bundle::failbackDraining);

            var enabled = bundle.enable();
            assertThat(enabled).succeedsWithin(Duration.ofSeconds(1));
            bundle.notify("com.evento.test.Stats", new byte[0]).get(5, TimeUnit.SECONDS);
            Thread.sleep(300);
            assertThat(b.available("shop-1")).as("re-enabled on the broker it is leaving").isFalse();
            assertThat(readyOn(bundle, "broker-b")).isTrue();

            release.countDown();
            assertThat(pending.get(10, TimeUnit.SECONDS).isError()).isFalse();
            await().atMost(Duration.ofSeconds(10)).until(() -> readyOn(bundle, "broker-a"));
            await().atMost(Duration.ofSeconds(5)).until(() -> a.available("shop-1"));
        } finally {
            release.countDown();
        }
    }

    /**
     * MEDIUM-LOW-7: the drain used to count an inbound request done when its reply
     * was handed to the transport, not when it was written. A reply large enough to
     * be chunked was then cut off by the close of the session.
     */
    @Test
    void aLargeReplyWrittenDuringTheDrainArrivesWhole() throws Exception {
        int aPort = deadPreferredPort();
        var b = brokerOn("broker-b", 0);
        int size = 40 * 1024 * 1024;  // several 16 MiB chunks
        var invoked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var handler = track(client("big", "big-1", Duration.ofMillis(300), aPort, b.port())
                .handlerPayloadTypes(List.of(BIG)).build());
        handler.registerRequestHandler(BIG, (payload, ctx) -> {
            invoked.countDown();
            release.await(30, TimeUnit.SECONDS);
            var body = new byte[size];
            Arrays.fill(body, (byte) 7);
            return body;
        });
        handler.start().get(10, TimeUnit.SECONDS);
        var caller = track(plain("caller", "caller-1", b.port()).build());
        caller.start().get(5, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> b.available("big-1"));

        try {
            var pending = caller.request(BIG, "x".getBytes(), Duration.ofSeconds(60));
            assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();
            brokerOn("broker-a", aPort);
            await().atMost(Duration.ofSeconds(10)).until(handler::failbackDraining);
            Thread.sleep(400);  // past the drain's grace: only the in-flight count holds it open
            release.countDown();

            var response = pending.get(60, TimeUnit.SECONDS);
            assertThat(response.isError()).as("%s", response.error()).isFalse();
            assertThat(response.payload()).hasSize(size);
            assertThat(response.payload()[size - 1]).isEqualTo((byte) 7);
            await().atMost(Duration.ofSeconds(10)).until(() -> readyOn(handler, "broker-a"));
        } finally {
            release.countDown();
        }
    }

    /**
     * MEDIUM-3: stop() racing the switch's immediate redial. The redial used to
     * overwrite CLOSING and skip the interruptible sleep, leaving a supervisor that
     * reconnected behind a stopped bundle. Several rounds, each stopping at the
     * first sign of the switch.
     */
    @Test
    void stopDuringAFailbackLeavesNothingRunning() throws Exception {
        int aPort = deadPreferredPort();
        var b = brokerOn("broker-b", 0);
        for (int round = 0; round < 8; round++) {
            String id = "shop-" + round;
            var bundle = track(client("shop", id, Duration.ofMillis(200), aPort, b.port())
                    .failbackDrainTimeout(Duration.ZERO).build());
            bundle.start().get(10, TimeUnit.SECONDS);
            assertThat(readyOn(bundle, "broker-b")).isTrue();
            var a = brokerOn("broker-a", aPort);
            await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(1))
                    .until(() -> bundle.failbackDraining() || !readyOn(bundle, "broker-b"));
            if (round % 2 == 1) Thread.sleep(round);  // land in different parts of the switch
            bundle.stop(Duration.ofMillis(500));

            Thread.sleep(700);
            assertThat(bundle.state()).as("round %d", round).isEqualTo(BundleClientState.CLOSED);
            assertThat(a.has(id)).as("round %d: a stopped bundle registered on A", round).isFalse();
            assertThat(b.has(id)).as("round %d: a stopped bundle still on B", round).isFalse();
            assertThat(probeThreadAlive(id)).as("round %d: probe event loop left running", round).isFalse();
            a.kill();
        }
    }

    /**
     * LOW-11: the probe's event loop goes away with the supervisor, whether it is
     * stopped or gives up, and a stop does not log WARNs of its own.
     */
    @Test
    void theProbeEventLoopIsReleasedOnStopAndOnGiveUpWithoutWarnings() throws Exception {
        var a = brokerOn("broker-a", 0);
        int aPort = a.port();
        var b = brokerOn("broker-b", 0);
        var stopped = track(client("shop", "shop-1", Duration.ofMinutes(5), aPort, b.port()).build());
        var bounded = NettyTransportConfig.defaults().withReconnectStrategy(new ExponentialBackoffWithJitter(
                Duration.ofMillis(50), Duration.ofMillis(100), 0.0, 2));
        var givesUp = track(client("shop", "shop-2", Duration.ofMinutes(5), aPort, b.port())
                .transportConfig(bounded).build());
        stopped.start().get(5, TimeUnit.SECONDS);
        givesUp.start().get(5, TimeUnit.SECONDS);
        a.kill();
        await().atMost(Duration.ofSeconds(10)).until(() -> readyOn(stopped, "broker-b") && readyOn(givesUp, "broker-b"));
        var restarted = brokerOn("broker-a", aPort);
        await().atMost(Duration.ofSeconds(10)).until(() -> probeThreadAlive("shop-1") && probeThreadAlive("shop-2")
                && stopped.failbackProbesAnswered() > 0);

        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        var watched = List.of(
                (Logger) LoggerFactory.getLogger(ConnectionSupervisor.class),
                (Logger) LoggerFactory.getLogger(com.evento.transport.netty.NettyClientTransport.class));
        watched.forEach(l -> l.addAppender(logs));
        try {
            stopped.stop(Duration.ofSeconds(1));
            await().atMost(Duration.ofSeconds(5)).until(() -> !probeThreadAlive("shop-1"));
            assertThat(logs.list).filteredOn(e -> e.getLevel().isGreaterOrEqual(Level.WARN)
                            && e.getFormattedMessage().contains("shop-1")
                            || e.getLevel().isGreaterOrEqual(Level.WARN)
                            && e.getLoggerName().endsWith("ConnectionSupervisor"))
                    .as("WARN lines logged by stop()").isEmpty();
        } finally {
            watched.forEach(l -> l.detachAppender(logs));
        }

        // Both brokers gone: the bounded strategy gives up, and takes the probe loop with it.
        b.kill();
        restarted.kill();
        await().atMost(Duration.ofSeconds(20)).until(() -> givesUp.state() == BundleClientState.CLOSED);
        await().atMost(Duration.ofSeconds(5)).until(() -> !probeThreadAlive("shop-2"));
    }
}
