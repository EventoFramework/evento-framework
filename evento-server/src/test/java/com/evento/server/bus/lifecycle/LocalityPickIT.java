package com.evento.server.bus.lifecycle;

import com.evento.application.client.BundleClient;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Locality-aware handler selection (G12) over real TCP.
 *
 * <p>One broker running {@code LOCALITY_PREFERRED}; three replicas of the same
 * handler bundle in zones EU, AM and APAC, each answering with its own zone; a
 * caller in AM. While the AM replica is up every request stays in AM. Once it
 * is gone the broker must not refuse the request: locality is a preference, so
 * the calls spread over the replicas that are left.</p>
 *
 * <p>The same holds for requests the broker answers through a server-local
 * handler that forwards on its own ({@code CommandBrokerHandler}): the pick is
 * made on behalf of the bundle that sent the original request, not on behalf of
 * the server.</p>
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class LocalityPickIT {

    private static final String CMD = "com.evento.test.RegionCmd";
    private static final String BROKERED_CMD = "com.evento.test.BrokeredRegionCmd";
    private static final List<String> ZONES = List.of("EU", "AM", "APAC");

    private BusLifecycle lifecycle;
    private ClusterRegistry cluster;
    private int port;
    private final List<BundleClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        cluster = new ClusterRegistry(connections, ClusterRegistry.PickStrategy.LOCALITY_PREFERRED);
        lifecycle = new BusLifecycle(new NettyServerTransport(NettyTransportConfig.defaults()),
                connections, cluster, new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, "broker-locality",
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), new JacksonCborPayloadCodec(),
                TokenValidator.acceptAll(),
                new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        port = lifecycle.start(0);
    }

    @AfterEach
    void tearDown() {
        for (var c : clients) {
            try { c.close(); } catch (Exception ignored) { /* best effort */ }
        }
        lifecycle.stop(Duration.ofMillis(500));
    }

    private BundleClient client(String bundleId, String instanceId, String zone, List<String> handles) {
        var c = BundleClient.builder(bundleId, instanceId)
                .host("127.0.0.1").port(port)
                .bundleVersion("1")
                .zone(zone)
                .handlerPayloadTypes(handles)
                .transportConfig(NettyTransportConfig.defaults())
                .defaultRequestTimeout(Duration.ofSeconds(5))
                .build();
        clients.add(c);
        return c;
    }

    /** Starts one "region-handler" replica per zone, each answering with its zone name. */
    private Map<String, BundleClient> startHandlers(String payloadType) throws Exception {
        var handlers = new java.util.LinkedHashMap<String, BundleClient>();
        for (var zone : ZONES) {
            var h = client("region-handler", "handler-" + zone, zone, List.of(payloadType));
            h.registerRequestHandler(payloadType, (payload, ctx) -> zone.getBytes());
            h.start().get(5, TimeUnit.SECONDS);
            handlers.put(zone, h);
        }
        await().atMost(Duration.ofSeconds(5))
                .until(() -> cluster.availableNodesFor(payloadType).size() == ZONES.size());
        return handlers;
    }

    private List<String> call(BundleClient caller, String payloadType, int times) throws Exception {
        var answers = new ArrayList<String>();
        for (int i = 0; i < times; i++) {
            var response = caller.request(payloadType, new byte[0]).get(5, TimeUnit.SECONDS);
            assertThat(response.isError())
                    .as("request %d failed: %s", i, response.error())
                    .isFalse();
            answers.add(new String(response.payload()));
        }
        return answers;
    }

    @Test
    void requestsStayInTheCallersZoneAndFallBackWhenItsHandlerDies() throws Exception {
        var handlers = startHandlers(CMD);
        var caller = client("region-caller", "caller-AM", "AM", List.of());
        caller.start().get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(5))
                .until(() -> "AM".equals(lifecycle.zones().get("caller-AM")));
        assertThat(lifecycle.zones())
                .containsEntry("handler-EU", "EU")
                .containsEntry("handler-AM", "AM")
                .containsEntry("handler-APAC", "APAC");

        assertThat(call(caller, CMD, 40)).containsOnly("AM");

        handlers.get("AM").close();
        await().atMost(Duration.ofSeconds(10))
                .until(() -> cluster.availableNodesFor(CMD).size() == 2);
        assertThat(lifecycle.zones()).doesNotContainKey("handler-AM");

        var afterFailover = call(caller, CMD, 40);
        // No request is refused, none reaches the dead replica, and the fallback is
        // a spread over what is left rather than a new fixed preference.
        assertThat(new HashSet<>(afterFailover)).containsExactlyInAnyOrder("EU", "APAC");
    }

    @Test
    void aCallerWithoutAZoneIsSpreadOverEveryZone() throws Exception {
        startHandlers(CMD);
        var caller = client("region-caller", "caller-nowhere", null, List.of());
        caller.start().get(5, TimeUnit.SECONDS);

        assertThat(new HashSet<>(call(caller, CMD, 60)))
                .containsExactlyInAnyOrder("EU", "AM", "APAC");
    }

    /**
     * A server-local handler that forwards on its own (the shape of
     * {@code CommandBrokerHandler.forwardOrRetry}) receives the originator and
     * picks on its behalf.
     */
    @Test
    void aServerLocalForwardPicksInTheOriginatorsZone() throws Exception {
        startHandlers(CMD);
        lifecycle.registerLocalHandler(BROKERED_CMD, (payload, originator) -> {
            var destination = cluster.pickFor(CMD, originator).orElseThrow();
            var response = lifecycle.forward(destination, CMD, payload, Duration.ofSeconds(5))
                    .get(5, TimeUnit.SECONDS);
            return response.payload();
        });
        // The caller routes BROKERED_CMD to the broker because the broker answers it locally.
        var caller = client("region-caller", "caller-APAC", "APAC", List.of());
        caller.start().get(5, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5))
                .until(() -> "APAC".equals(lifecycle.zones().get("caller-APAC")));

        assertThat(call(caller, BROKERED_CMD, 30)).containsOnly("APAC");
    }
}
