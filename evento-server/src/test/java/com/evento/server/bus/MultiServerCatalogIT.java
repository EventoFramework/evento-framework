package com.evento.server.bus;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.evento.application.client.BundleClient;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.common.modeling.messaging.message.internal.EventoMessage;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleConsumerRegistrationMessage;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.server.EventoServerApplication;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.service.discovery.BundlePresence;
import com.evento.transport.codec.JacksonCborCodec;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.protocol.PayloadDiscoveryInfo;
import com.evento.transport.protocol.ProtocolPayloadTypes;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Two complete evento-server application contexts sharing ONE real Postgres catalog,
 * with real bundle clients over real TCP: the multi-region cluster shape in miniature.
 *
 * <ul>
 *   <li><b>G7</b>: an instance fails over from server A to server B while A still holds
 *       its (now stale) session. When A finally drops that session, its NodeLeft must
 *       leave B's registration intact; before the fix it cleared the instance's consumer
 *       rows and unregistered the bundle cluster-wide.</li>
 *   <li><b>G8</b>: a mass reconnect, with every bundle joining both servers at once and
 *       all of them sharing payload names, must converge on a complete catalog without a
 *       single {@code duplicate key} failure in discovery or consumer registration.</li>
 * </ul>
 *
 * <p>Both servers live in one JVM, so {@code PgDistributedLock}'s JVM-local semaphore
 * (a static map) serializes equal lock keys before Postgres does. The G8 race is still
 * real: different bundles take different keys yet write the same payload rows.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class MultiServerCatalogIT {

    private static final String SERVER_A = "server-a";
    private static final String SERVER_B = "server-b";

    private PostgreSQLContainer postgres;
    private ConfigurableApplicationContext serverA;
    private ConfigurableApplicationContext serverB;
    private int busPortA;
    private int busPortB;
    private JdbcTemplate jdbc;
    private NettyTransportConfig clientTransport;
    private final AdminPayloadCodec adminCodec = new AdminPayloadCodec();
    private final List<BundleClient> clients = new CopyOnWriteArrayList<>();
    private ListAppender<ILoggingEvent> logs;

    @BeforeAll
    void startCluster() {
        postgres = new PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("evento")
                .withUsername("evento")
                .withPassword("evento");
        postgres.start();
        serverA = boot(SERVER_A);
        serverB = boot(SERVER_B);
        busPortA = serverA.getBean(BusLifecycle.class).boundPort();
        busPortB = serverB.getBean(BusLifecycle.class).boundPort();
        jdbc = serverA.getBean(JdbcTemplate.class);
        clientTransport = new NettyTransportConfig(
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(5),
                16 * 1024 * 1024, 64 * 1024, 32 * 1024,
                new ExponentialBackoffWithJitter(), new JacksonCborCodec(),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    private ConfigurableApplicationContext boot(String serverId) {
        // Command-line args, not builder default properties: application.properties
        // outranks defaults and would pin both servers to the same ports and identity.
        return new SpringApplicationBuilder(EventoServerApplication.class)
                .run(
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--spring.datasource.hikari.maximum-pool-size=20",
                        "--server.port=0",
                        "--evento.server.bus.port=0",
                        "--evento.cluster.node.server.id=" + serverId,
                        "--evento.server.instance.id=" + serverId,
                        "--spring.main.banner-mode=off");
    }

    @AfterAll
    void stopCluster() {
        if (serverA != null) serverA.close();
        if (serverB != null) serverB.close();
        if (postgres != null) postgres.stop();
    }

    @BeforeEach
    void captureLogs() {
        logs = new ListAppender<>();
        logs.start();
        rootLogger().addAppender(logs);
    }

    /**
     * Closing every client at once is itself a mass departure: both servers remove
     * the bundles concurrently. Wait until every leave is processed and hold that
     * cleanup to the same no-error bar as the joins.
     */
    @AfterEach
    void closeClientsAndDrain() {
        try {
            clients.forEach(BundleClient::close);
            clients.clear();
            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(serverA.getBean(BusFacade.class).currentView()).isEmpty();
                assertThat(serverB.getBean(BusFacade.class).currentView()).isEmpty();
                assertThat(jdbc.queryForObject("select count(*) from core__bundle_instance", Integer.class))
                        .isZero();
                assertThat(jdbc.queryForObject("select count(*) from core__bundle", Integer.class))
                        .isZero();
            });
            assertNoDiscoveryErrors();
        } finally {
            rootLogger().detachAppender(logs);
        }
    }

    private static ch.qos.logback.classic.Logger rootLogger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    // ---------------------------------------------------------------- G7

    @Test
    void staleLeaveOnTheOldServerKeepsTheRegistrationOfAnInstanceThatMovedAway() {
        var bundle = "g7-query-sud_america";
        var instance = "g7-instance-1";
        var consumerId = bundle + "_G7RegionProjector_1_SUD_AMERICA";
        var handlers = List.of(eventHandler("G7RegionProjector", "G7OrderCreated"));

        var onA = startClient(bundle, instance, busPortA, handlers);
        registerConsumers(onA, bundle, instance, "G7RegionProjector", consumerId);
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(owner(instance)).isEqualTo(SERVER_A);
            assertThat(consumerRows(instance)).isEqualTo(1);
        });

        // The instance fails over to B while A still holds its half-dead session.
        var onB = startClient(bundle, instance, busPortB, handlers);
        registerConsumers(onB, bundle, instance, "G7RegionProjector", consumerId);
        await().atMost(15, TimeUnit.SECONDS).until(() -> SERVER_B.equals(owner(instance)));

        // A finally notices its session is gone.
        onA.close();
        await().atMost(15, TimeUnit.SECONDS).until(() ->
                serverA.getBean(BusFacade.class).currentView().isEmpty());

        await().during(2, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(bundleRows(bundle)).as("bundle row").isEqualTo(1);
            assertThat(componentRows("G7RegionProjector")).as("component row").isEqualTo(1);
            assertThat(handlerRows("G7RegionProjector")).as("handler rows").isEqualTo(1);
            assertThat(consumerRows(instance)).as("consumer rows of the moved instance").isEqualTo(1);
            assertThat(owner(instance)).isEqualTo(SERVER_B);
        });

        // The owner's own departure still reclaims the ephemeral registration.
        onB.close();
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(bundleRows(bundle)).isZero();
            assertThat(consumerRows(instance)).isZero();
            assertThat(owner(instance)).isNull();
        });
        assertNoDiscoveryErrors();
    }

    @Test
    void rowsOfAServerThatStoppedHeartbeatingNoLongerKeepABundleAlive() {
        var presence = serverA.getBean(BundlePresence.class);
        var bundle = "g7-crashed-server-bundle";
        // A server that crashed and never came back left a row behind, long unrefreshed.
        jdbc.update("""
                insert into core__bundle_instance (instance_id, bundle_id, server_instance_id, updated_at)
                values ('ghost-instance', ?, 'server-gone', now() - interval '1 hour')
                """, bundle);
        try {
            assertThat(presence.liveElsewhere(bundle, "local-instance")).isFalse();
            jdbc.update("update core__bundle_instance set updated_at = now() where instance_id = 'ghost-instance'");
            assertThat(presence.liveElsewhere(bundle, "local-instance")).isTrue();

            // A leave of an instance whose only row belongs to a dead server takes over the cleanup.
            jdbc.update("update core__bundle_instance set updated_at = now() - interval '1 hour'"
                    + " where instance_id = 'ghost-instance'");
            assertThat(presence.release(new NodeAddress(bundle, 1, "ghost-instance")))
                    .isEqualTo(BundlePresence.Release.OWNED);

            // The heartbeat re-creates a connected instance's missing row, refreshes its own
            // rows, never steals another server's live row, and reaps its unrefreshed ones.
            jdbc.update("""
                    insert into core__bundle_instance (instance_id, bundle_id, server_instance_id, updated_at)
                    values ('held-by-b', ?, ?, now()), ('stale-of-a', ?, ?, now() - interval '1 hour')
                    """, bundle, SERVER_B, bundle, SERVER_A);
            presence.heartbeat(List.of(new NodeAddress(bundle, 1, "held-by-b"),
                    new NodeAddress(bundle, 1, "connected-to-a")));
            assertThat(owner("held-by-b")).isEqualTo(SERVER_B);
            assertThat(owner("connected-to-a")).isEqualTo(SERVER_A);
            assertThat(owner("stale-of-a")).isNull();
        } finally {
            jdbc.update("delete from core__bundle_instance where bundle_id = ?", bundle);
        }
    }

    // ---------------------------------------------------------------- G8

    @Test
    void massReconnectOfEveryBundleToBothServersAtOnceConvergesWithoutDuplicateKeys() throws Exception {
        int bundles = 6;
        var go = new CountDownLatch(1);
        var starts = new ArrayList<CompletableFuture<Void>>();
        var instances = new ArrayList<String>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int b = 0; b < bundles; b++) {
                var bundle = "g8-bundle-" + b;
                var projector = "G8Projector" + b;
                var aggregate = "G8Aggregate" + b;
                // Every bundle shares the same payload names: the rows no per-bundle lock covers.
                var handlers = List.of(
                        eventHandler(projector, "G8SharedEvent"),
                        commandHandler(aggregate, "G8SharedCommand", "G8SharedEvent"));
                for (var port : List.of(busPortA, busPortB)) {
                    var instance = bundle + "@" + (port == busPortA ? SERVER_A : SERVER_B);
                    instances.add(instance);
                    starts.add(CompletableFuture.runAsync(() -> {
                        try {
                            go.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        var client = startClient(bundle, instance, port, handlers);
                        registerConsumers(client, bundle, instance, projector,
                                bundle + "_" + projector + "_1_AUSTRALIA_NZ");
                    }, pool));
                }
            }
            go.countDown();
            CompletableFuture.allOf(starts.toArray(CompletableFuture[]::new)).get(60, TimeUnit.SECONDS);
        }

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(jdbc.queryForObject(
                    "select count(*) from core__bundle where id like 'g8-bundle-%'", Integer.class))
                    .isEqualTo(bundles);
            assertThat(jdbc.queryForObject(
                    "select count(*) from core__handler h join core__component c"
                            + " on h.component_component_name = c.component_name"
                            + " where c.bundle_id like 'g8-bundle-%'", Integer.class))
                    .isEqualTo(bundles * 2);
            assertThat(jdbc.queryForObject(
                    "select count(*) from core__payload where name in ('G8SharedEvent', 'G8SharedCommand')",
                    Integer.class)).isEqualTo(2);
            for (var instance : instances) {
                assertThat(consumerRows(instance)).as("consumers of " + instance).isEqualTo(1);
            }
            assertThat(jdbc.queryForObject(
                    "select count(*) from core__bundle_instance where bundle_id like 'g8-bundle-%'",
                    Integer.class)).isEqualTo(bundles * 2);
        });
        assertNoDiscoveryErrors();
    }

    /**
     * The shape that deadlocked the one-transaction discovery pass: every bundle declares
     * handlers on the same shared payloads, each in its own order, and reports metadata
     * for all of them. The rows start with the wrong type and stale metadata, so every
     * pass wants to correct them. Updating shared rows inside the discovery transaction
     * locked them in handler order until commit; two bundles with different orders then
     * formed a lock cycle and Postgres aborted one pass with 40P01.
     */
    @Test
    void bundlesDeclaringSharedPayloadsInDifferentOrdersDoNotDeadlock() throws Exception {
        int bundles = 6;
        int shared = 10;
        var names = new ArrayList<String>();
        for (int i = 0; i < shared; i++) {
            var name = "G8OrderedEvent" + i;
            names.add(name);
            jdbc.update("""
                    insert into core__payload (name, type, registered_in, json_schema, is_valid_json_schema, updated_at)
                    values (?, 'Command', null, 'null', false, now())
                    """, name);
        }
        var go = new CountDownLatch(1);
        var starts = new ArrayList<CompletableFuture<Void>>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int b = 0; b < bundles; b++) {
                var bundle = "g8-ordered-" + b;
                var projector = "G8OrderedProjector" + b;
                var order = new ArrayList<>(names);
                Collections.shuffle(order, new Random(b));
                var handlers = order.stream().map(n -> eventHandler(projector, n)).toList();
                var info = new LinkedHashMap<String, PayloadDiscoveryInfo>();
                for (var n : order) {
                    info.put(n, new PayloadDiscoveryInfo("{\"title\":\"" + n + "\"}", "orders",
                            "reported by " + bundle, "", "com/example/" + n + ".java", 7));
                }
                for (var port : List.of(busPortA, busPortB)) {
                    var instance = bundle + "@" + (port == busPortA ? SERVER_A : SERVER_B);
                    starts.add(CompletableFuture.runAsync(() -> {
                        try {
                            go.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        startClient(bundle, instance, port, handlers, info);
                    }, pool));
                }
            }
            go.countDown();
            CompletableFuture.allOf(starts.toArray(CompletableFuture[]::new)).get(60, TimeUnit.SECONDS);
        }

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(jdbc.queryForObject(
                    "select count(*) from core__handler h join core__component c"
                            + " on h.component_component_name = c.component_name"
                            + " where c.bundle_id like 'g8-ordered-%'", Integer.class))
                    .isEqualTo(bundles * shared);
            assertThat(jdbc.queryForObject("""
                    select count(*) from core__payload
                    where name like 'G8OrderedEvent%' and type = 'DomainEvent'
                      and is_valid_json_schema and domain = 'orders' and line = 7
                    """, Integer.class)).isEqualTo(shared);
        });
        assertNoDiscoveryErrors();
        assertThat(logs.list).noneMatch(e -> String.valueOf(e.getFormattedMessage()).contains("deadlock")
                || (e.getThrowableProxy() != null
                && String.valueOf(e.getThrowableProxy().getMessage()).contains("deadlock")));
    }

    // ---------------------------------------------------------------- helpers

    private BundleClient startClient(String bundleId, String instanceId, int port,
                                     List<RegisteredHandler> handlers) {
        return startClient(bundleId, instanceId, port, handlers, Map.of());
    }

    private BundleClient startClient(String bundleId, String instanceId, int port,
                                     List<RegisteredHandler> handlers,
                                     Map<String, PayloadDiscoveryInfo> payloadInfo) {
        var client = BundleClient.builder(bundleId, instanceId)
                .host("127.0.0.1").port(port).bundleVersion("1")
                .transportConfig(clientTransport)
                .payloadInfo(payloadInfo)
                .registeredHandlers(handlers)
                .handlerPayloadTypes(handlers.stream()
                        .filter(h -> h.getHandlerType() != HandlerType.EventHandler)
                        .map(RegisteredHandler::getHandledPayload).toList())
                .build();
        clients.add(client);
        client.start().orTimeout(10, TimeUnit.SECONDS).join();
        return client;
    }

    private void registerConsumers(BundleClient client, String bundleId, String instanceId,
                                   String componentName, String consumerId) {
        var cr = new BundleConsumerRegistrationMessage();
        var projectors = new HashMap<String, HashSet<String>>();
        projectors.put(componentName, new HashSet<>(Set.of(consumerId)));
        cr.setProjectorConsumers(projectors);
        cr.setSagaConsumers(new HashMap<>());
        cr.setObserverConsumers(new HashMap<>());
        var envelope = new EventoMessage();
        envelope.setSourceBundleId(bundleId);
        envelope.setSourceInstanceId(instanceId);
        envelope.setSourceBundleVersion(1);
        envelope.setBody(cr);
        client.notify(ProtocolPayloadTypes.BUNDLE_ADMIN_NOTIFICATION, adminCodec.encodeMessage(envelope))
                .orTimeout(10, TimeUnit.SECONDS).join();
    }

    private static RegisteredHandler eventHandler(String projector, String event) {
        return new RegisteredHandler(ComponentType.Projector, projector, HandlerType.EventHandler,
                PayloadType.DomainEvent, event, null, false, null);
    }

    private static RegisteredHandler commandHandler(String aggregate, String command, String event) {
        return new RegisteredHandler(ComponentType.Aggregate, aggregate, HandlerType.AggregateCommandHandler,
                PayloadType.DomainCommand, command, event, false, "id");
    }

    private String owner(String instanceId) {
        return jdbc.queryForList("select server_instance_id from core__bundle_instance where instance_id = ?",
                String.class, instanceId).stream().findFirst().orElse(null);
    }

    private int consumerRows(String instanceId) {
        return jdbc.queryForObject("select count(*) from core__consumer where instance_id = ?",
                Integer.class, instanceId);
    }

    private int bundleRows(String bundleId) {
        return jdbc.queryForObject("select count(*) from core__bundle where id = ?", Integer.class, bundleId);
    }

    private int componentRows(String componentName) {
        return jdbc.queryForObject("select count(*) from core__component where component_name = ?",
                Integer.class, componentName);
    }

    private int handlerRows(String componentName) {
        return jdbc.queryForObject("select count(*) from core__handler where component_component_name = ?",
                Integer.class, componentName);
    }

    /** Discovery and registration swallow their failures into ERROR logs; surface them. */
    private void assertNoDiscoveryErrors() {
        var errors = logs.list.stream()
                .filter(e -> e.getLevel().isGreaterOrEqual(Level.ERROR))
                .filter(e -> e.getLoggerName().startsWith("com.evento.server.service"))
                .map(e -> e.getLoggerName() + ": " + e.getFormattedMessage()
                        + (e.getThrowableProxy() == null ? "" : " / " + e.getThrowableProxy().getMessage()))
                .toList();
        assertThat(errors).isEmpty();
        assertThat(logs.list).noneMatch(e -> String.valueOf(e.getFormattedMessage()).contains("duplicate key")
                || (e.getThrowableProxy() != null
                && String.valueOf(e.getThrowableProxy().getMessage()).contains("duplicate key")));
    }
}
