package com.evento.lab.ms.it;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.common.messaging.consumer.ConsumerProcessor;
import com.evento.common.serialization.ObjectMapperUtils;
import com.evento.consumer.state.store.jdbc.FlywayMigrator;
import com.evento.consumer.state.store.jdbc.JdbcConsumerLock;
import com.evento.consumer.state.store.jdbc.JdbcConsumerStateStore;
import com.evento.consumer.state.store.jdbc.JdbcDeadEventQueue;
import com.evento.consumer.state.store.jdbc.JdbcDedupeStore;
import com.evento.consumer.state.store.jdbc.JdbcSagaStateStore;
import com.evento.consumer.state.store.jdbc.SqlDialect;
import com.evento.lab.ms.api.event.NotificationSentEvent;
import com.evento.lab.ms.api.event.OrderCreatedEvent;
import com.evento.lab.ms.it.support.MsEmbeddedBroker;
import com.evento.lab.ms.it.support.MsTestEventStore;
import com.evento.lab.ms.query.LabMsQueryApplication;
import com.evento.lab.ms.query.projector.OrderProjector;
import com.evento.lab.ms.query.store.ConsumptionJournal;
import com.evento.lab.ms.query.store.OrderViewStore;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.protocol.ProtocolNotifications;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * High availability of consumers split by context.
 *
 * <p>The read side runs one consumer per context ({@code OrderProjector} bound to IT, UK or DE,
 * each in a bundle of its own) and every one of them in three replicas, as three instances of a
 * service would: their own connection pool each, one PostgreSQL for the consumer locks and
 * checkpoints, one broker that tells them when the event store grows (head notices) while their
 * fetch delay is long enough that nothing else would move them.
 *
 * <p>While the events of the three contexts are published, interleaved with events no consumer
 * here handles, replicas are taken down two at a time per context until one is left (a process
 * dying: engines stopped, pool closed, so its locks go with its sessions). Whatever replica holds
 * a context's lane, the projector must apply every event of that context exactly once, in the
 * order of the stream, and no event of another context.
 *
 * <p>Needs Docker; runs with {@code EVENTO_RUN_JDBC_IT=true}, like the other database ITs.
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true")
@Timeout(value = 300, unit = TimeUnit.SECONDS)
class MsHighAvailabilityConsumersIT {

    private static final List<String> CONTEXTS = List.of("IT", "UK", "DE");
    private static final int REPLICAS = 3;
    private static final int ROUNDS = 90;
    /** Long enough that only the head notices explain a consumer keeping up. */
    private static final int FETCH_DELAY_MS = 15_000;

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17-alpine");

    private MsEmbeddedBroker broker;
    private MsTestEventStore eventStore;
    private final Map<String, List<Replica>> replicas = new LinkedHashMap<>();

    /** One instance of a context's consumer service: its bundle and its own pool, named in PostgreSQL. */
    private record Replica(String name, EventoBundle bundle, HikariDataSource pool) {
        void kill() {
            bundle.getEngineSupervisor().stop(Duration.ofSeconds(10));
            pool.close();  // its sessions, and the locks they held, go with it
        }
    }

    @BeforeAll
    static void migrate() {
        try (var ds = pool(2)) {
            FlywayMigrator.migrate(ds, SqlDialect.POSTGRES);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        OrderViewStore.reset();
        ConsumptionJournal.reset();
        try (var ds = pool(1); var c = ds.getConnection(); var st = c.createStatement()) {
            st.execute("DELETE FROM evento_v2_consumer_state");
            st.execute("DELETE FROM evento_v2_dead_event");
        }
        broker = new MsEmbeddedBroker(Set.of(HandshakeProtocol.CAPABILITY_HEAD));
        eventStore = new MsTestEventStore(broker.port());
    }

    @AfterEach
    void tearDown() {
        replicas.values().forEach(list -> list.forEach(r -> {
            try {
                r.kill();
            } catch (RuntimeException ignored) {
                // already down
            }
        }));
        replicas.clear();
        if (eventStore != null) eventStore.close();
        if (broker != null) broker.close();
    }

    private static HikariDataSource pool(int size) {
        return pool(size, "ha-admin");
    }

    private static HikariDataSource pool(int size, String applicationName) {
        var cfg = new HikariConfig();
        cfg.addDataSourceProperty("ApplicationName", applicationName);
        cfg.setJdbcUrl(PG.getJdbcUrl());
        cfg.setUsername(PG.getUsername());
        cfg.setPassword(PG.getPassword());
        cfg.setMaximumPoolSize(size);
        return new HikariDataSource(cfg);
    }

    private static String bundleIdOf(String context) {
        return "lab-ms-ha-" + context.toLowerCase();
    }

    private Replica startReplica(String context, int index) throws Exception {
        var name = "ha-" + context + "-" + index;
        var ds = pool(8, name);
        var bundle = EventoBundle.Builder.builder()
                .setBasePackage(LabMsQueryApplication.class.getPackage())
                .setBundleId(bundleIdOf(context))
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setComponentContexts(OrderProjector.class, context)
                .setSssFetchDelay(FETCH_DELAY_MS)
                .setConsumerEngineConfigBuilder((es, ps) -> {
                    var mapper = ObjectMapperUtils.getPayloadObjectMapper();
                    var stateStore = new JdbcConsumerStateStore(ds, SqlDialect.POSTGRES);
                    var dlq = new JdbcDeadEventQueue(ds, SqlDialect.POSTGRES, mapper);
                    var processor = ConsumerProcessor.builder()
                            .eventoServer(es)
                            .lock(new JdbcConsumerLock(ds, SqlDialect.POSTGRES))
                            .stateStore(stateStore)
                            .sagaStateStore(new JdbcSagaStateStore(ds, SqlDialect.POSTGRES, mapper))
                            .deadEventQueue(dlq)
                            .dedupeStore(new JdbcDedupeStore(ds, SqlDialect.POSTGRES))
                            .performanceService(ps)
                            .observerExecutor(Executors.newVirtualThreadPerTaskExecutor())
                            .build();
                    return new ConsumerEngineConfig(processor, stateStore, dlq);
                })
                .start();
        return new Replica(name, bundle, ds);
    }

    /** What the server's head notifier would send: the new head and the names appended since. */
    private void announce(long head, String... eventNames) {
        var names = String.join("\n", eventNames).getBytes(StandardCharsets.UTF_8);
        broker.lifecycle().broadcast(HandshakeProtocol.CAPABILITY_HEAD, ProtocolNotifications.EVENTS_APPENDED,
                ByteBuffer.allocate(Long.BYTES + names.length).putLong(head).put(names).array());
    }

    /** The replica whose session holds the advisory lock of the context's projector lane, if any. */
    private String laneHolder(String context) throws Exception {
        var consumerId = bundleIdOf(context) + "_OrderProjector_1_" + context;
        try (var ds = pool(1); var c = ds.getConnection(); var ps = c.prepareStatement(
                "SELECT a.application_name FROM pg_locks l JOIN pg_stat_activity a ON a.pid = l.pid "
                        + "WHERE l.locktype = 'advisory' AND l.granted "
                        + "AND ((l.classid::bigint << 32) | l.objid::bigint) = hashtext(?)::bigint")) {
            ps.setString(1, consumerId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Takes down the replica holding the context's lane (the first one when none holds it now). */
    private String killHolder(String context) throws Exception {
        var list = replicas.get(context);
        var holder = laneHolder(context);
        var victim = list.stream().filter(r -> r.name().equals(holder)).findFirst().orElse(list.getFirst());
        list.remove(victim);
        victim.kill();
        return holder == null ? null : victim.name();
    }

    private static String orderId(String context, int round) {
        return "%s-%03d".formatted(context, round);
    }

    @Test
    void eachContextIsConsumedOnceAndInOrderWhileItsReplicasGoDown() throws Exception {
        for (var context : CONTEXTS) {
            var list = new ArrayList<Replica>();
            for (int i = 0; i < REPLICAS; i++) list.add(startReplica(context, i));
            replicas.put(context, list);
        }
        await().atMost(60, TimeUnit.SECONDS).until(() -> CONTEXTS.stream()
                .allMatch(c -> broker.lifecycle().isBundleAvailable(bundleIdOf(c))));
        Thread.sleep(1_500);  // every engine past its first fetch, waiting at the head

        long head = 0;
        var holdersKilled = new LinkedHashMap<String, List<String>>();
        for (int round = 0; round < ROUNDS; round++) {
            for (var context : CONTEXTS) {
                head = eventStore.publishWithContext(
                        new OrderCreatedEvent(orderId(context, round), "order " + round, round, context),
                        orderId(context, round), context);
                if (round % 3 == 0) {
                    // An event no consumer here handles, in the same lane.
                    head = eventStore.publishServiceEventWithContext(
                            new NotificationSentEvent(orderId(context, round), "noise", "EMAIL"), context);
                }
            }
            if (round % 5 == 4) {
                announce(head, "OrderCreatedEvent", "NotificationSentEvent");
                Thread.sleep(100);
            }
            if (round == ROUNDS / 3 || round == 2 * ROUNDS / 3) {
                // The replica of each context that holds its lane goes down, mid-stream.
                for (var context : CONTEXTS) {
                    var killed = killHolder(context);
                    if (killed != null) holdersKilled.computeIfAbsent(context, c -> new ArrayList<>()).add(killed);
                }
            }
        }
        announce(head, "OrderCreatedEvent", "NotificationSentEvent");

        await().atMost(90, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(200)).until(() -> CONTEXTS.stream()
                .allMatch(c -> ConsumptionJournal.of(c).size() >= ROUNDS));
        Thread.sleep(2_000);  // a late duplicate would show up here

        for (var context : CONTEXTS) {
            var expected = new ArrayList<String>();
            for (int round = 0; round < ROUNDS; round++) expected.add(orderId(context, round));
            assertThat(ConsumptionJournal.of(context))
                    .as("context %s: every event once, in the order of the stream, none of another context", context)
                    .containsExactlyElementsOf(expected);
            assertThat(replicas.get(context)).as("one replica of %s left", context).hasSize(1);
            assertThat(holdersKilled.getOrDefault(context, List.of()))
                    .as("the lane of %s changed hands twice: its holder was taken down each time", context)
                    .hasSize(2);
        }
    }
}
