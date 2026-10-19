package com.evento.server.es.pg;

import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.serialization.ObjectMapperUtils;
import com.evento.server.es.BrokerEventStore;
import com.evento.server.es.EventStore;
import com.evento.server.es.EventStoreMode;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The aggregate fence (C1) at the store level, on both event store modes, against a real
 * Postgres with the production schema: the story is read with the aggregate's version, an
 * append from a version that is no longer the latest is refused by the database, and an
 * existing table gets the column and the index online.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class AggregateFencePostgresIT {

    private static final String COMMAND = "OrderCommand";

    private PostgreSQLContainer postgres;
    private HikariDataSource serverA;
    private HikariDataSource serverB;
    private JdbcTemplate jdbc;

    public static class OrderEvent extends DomainEvent implements Serializable {
        public String note;

        public OrderEvent() {
        }

        OrderEvent(String note) {
            this.note = note;
        }
    }

    @BeforeAll
    void start() throws Exception {
        postgres = PostgresItSupport.newContainer();
        postgres.start();
        PostgresItSupport.createDatabase(postgres, "fence_it");
        String url = PostgresItSupport.jdbcUrl(postgres, "fence_it");
        serverA = PostgresItSupport.dataSource(postgres, url, 4);
        serverB = PostgresItSupport.dataSource(postgres, url, 4);
        PostgresItSupport.initializer(serverA, true).initializeDatabase();
        jdbc = new JdbcTemplate(serverA);
    }

    @AfterAll
    void stop() {
        if (serverA != null) serverA.close();
        if (serverB != null) serverB.close();
        if (postgres != null) postgres.stop();
    }

    private static EventStore store(DataSource ds, EventStoreMode mode, int node) throws Exception {
        var store = new EventStore(null, null, new JdbcTemplate(ds), ds, 64, 64, 150_000, 150_000, 69, mode);
        // Two stores in one JVM would derive the same APES node id from the host's MAC.
        var snowflake = EventStore.class.getDeclaredField("snowflake");
        snowflake.setAccessible(true);
        snowflake.set(store, new com.evento.common.utils.Snowflake(node));
        return store;
    }

    private static DomainEventMessage event(String note) {
        return new DomainEventMessage(new OrderEvent(note));
    }

    /** The story as a command sees it; no snapshot repository here, so the snapshot is skipped. */
    private static BrokerEventStore.KeyedAggregateStory story(EventStore store, String aggregateId) {
        return store.fetchAggregateStory(aggregateId, false, true, null);
    }

    private static String newAggregate() {
        return "order-" + UUID.randomUUID();
    }

    @ParameterizedTest
    @EnumSource(EventStoreMode.class)
    void theStoryCarriesTheVersionItEndsAtOnEveryServer(EventStoreMode mode) throws Exception {
        var a = store(serverA, mode, 1);
        var b = store(serverB, mode, 2);
        var order = newAggregate();
        assertThat(story(a, order).aggregateVersion()).isZero();

        a.appendAggregateEvent(event("created"), order, null, COMMAND, 0L);
        var onB = story(b, order);
        assertThat(onB.aggregateVersion()).isEqualTo(1);
        b.appendAggregateEvent(event("paid"), order, null, COMMAND, onB.aggregateVersion());

        var onA = story(a, order);
        assertThat(onA.aggregateVersion()).isEqualTo(2);
        assertThat(onA.story().events()).extracting(e -> ((OrderEvent) e.getPayload()).note)
                .containsExactly("created", "paid");
        assertThat(jdbc.queryForList("select aggregate_seq from es__events where aggregate_id = ? "
                + "order by event_sequence_number", Long.class, order)).containsExactly(1L, 2L);
    }

    @ParameterizedTest
    @EnumSource(EventStoreMode.class)
    void aSecondAppendFromTheSameStoryIsRefusedAndStoresNothing(EventStoreMode mode) throws Exception {
        // Two servers decided from the same story (a lost lock, or no lock at all).
        var a = store(serverA, mode, 1);
        var b = store(serverB, mode, 2);
        var order = newAggregate();
        a.appendAggregateEvent(event("created"), order, null, COMMAND, 0L);
        long seen = story(a, order).aggregateVersion();
        assertThat(story(b, order).aggregateVersion()).isEqualTo(seen);

        a.appendAggregateEvent(event("shipped by A"), order, null, COMMAND, seen);
        assertThatThrownBy(() -> b.appendAggregateEvent(event("cancelled by B"), order, null, COMMAND, seen))
                .isInstanceOf(BrokerEventStore.AggregateFenceException.class)
                .hasMessageContaining(order);

        assertThat(jdbc.queryForList("select event_message from es__events where aggregate_id = ?", String.class,
                order)).hasSize(2).noneMatch(m -> m.contains("cancelled by B"));
        // B runs again on the new story and goes through.
        var again = story(b, order);
        assertThat(again.aggregateVersion()).isEqualTo(2);
        b.appendAggregateEvent(event("cancelled by B"), order, null, COMMAND, again.aggregateVersion());
        assertThat(story(a, order).aggregateVersion()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(EventStoreMode.class)
    void eventsWithoutAVersionNeitherBlockNorMoveTheFence(EventStoreMode mode) throws Exception {
        // Events of servers before C1, and of service commands, carry no version.
        var a = store(serverA, mode, 1);
        var order = newAggregate();
        a.publishEvent(event("legacy"), order);
        var story = story(a, order);
        assertThat(story.aggregateVersion()).isZero();
        assertThat(story.story().events()).hasSize(1);

        a.appendAggregateEvent(event("fenced"), order, null, COMMAND, 0L);
        a.publishEvent(event("service"), order, null, null);
        assertThat(story(a, order).aggregateVersion()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from es__events where aggregate_id = ? "
                + "and aggregate_seq is null", Integer.class, order)).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(EventStoreMode.class)
    void aDuplicateKeyIsAnIdempotencyConflictNotAFenceConflict(EventStoreMode mode) throws Exception {
        // The broker replays an idempotency conflict; it must not see the fence first.
        var a = store(serverA, mode, 1);
        var b = store(serverB, mode, 2);
        var order = newAggregate();
        var key = "create-" + order;
        a.appendAggregateEvent(event("created"), order, key, COMMAND, 0L);
        assertThatThrownBy(() -> b.appendAggregateEvent(event("created again"), order, key, COMMAND, 0L))
                .isInstanceOf(BrokerEventStore.IdempotencyKeyConflictException.class);
        assertThat(jdbc.queryForObject("select aggregate_seq from es__events where idempotency_key = ?",
                Long.class, key)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(EventStoreMode.class)
    void aCachedStoryThatMissedAnEventIsFetchedAgainInsteadOfFencedAtTheWrongVersion(EventStoreMode mode)
            throws Exception {
        // Server A caches the story; another server then appends version 3 with a sequence number
        // inside A's cached range (an APES id from a server whose clock is behind). A's cache-based
        // fetch only reads around that range, so it would skip the event and fence at version 3
        // a story that ends at version 2.
        var a = store(serverA, mode, 1);
        var order = newAggregate();
        long first = a.appendAggregateEvent(event("created"), order, null, COMMAND, 0L);
        if (mode == EventStoreMode.CPES) {
            jdbc.queryForList("select nextval('event_sequence_number_serial') from generate_series(1, 20)");
        } else {
            Thread.sleep(5);
        }
        long second = a.appendAggregateEvent(event("paid"), order, null, COMMAND, 1L);
        assertThat(story(a, order).aggregateVersion()).isEqualTo(2);
        var message = ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(event("late"));
        jdbc.update("insert into es__events (event_sequence_number, aggregate_id, event_message, event_name, context, "
                + "aggregate_seq) values (?, ?, ?, 'OrderEvent', 'default', 3)", (first + second) / 2, order, message);

        var story = story(a, order);
        assertThat(story.aggregateVersion()).isEqualTo(3);
        assertThat(story.story().events()).extracting(e -> ((OrderEvent) e.getPayload()).note)
                .containsExactly("created", "late", "paid");
    }

    @ParameterizedTest
    @EnumSource(EventStoreMode.class)
    void anExistingTableGetsTheFenceOnlineAndUntilThenAppendsUnfenced(EventStoreMode mode) throws Exception {
        String db = "fence_upgrade_" + mode.name().toLowerCase();
        PostgresItSupport.createDatabase(postgres, db);
        try (var ds = PostgresItSupport.dataSource(postgres, PostgresItSupport.jdbcUrl(postgres, db), 3)) {
            PostgresItSupport.initializer(ds, true).initializeDatabase();
            var pg = new JdbcTemplate(ds);
            // The table of a server before C1.
            pg.execute("DROP INDEX " + EventStore.AGGREGATE_SEQ_INDEX);
            pg.execute("ALTER TABLE es__events DROP COLUMN aggregate_seq");
            var before = store(ds, mode, 1);
            var order = newAggregate();
            assertThat(before.fenceSupported()).isFalse();
            assertThat(story(before, order).aggregateVersion()).isNull();
            before.appendAggregateEvent(event("old"), order, null, COMMAND, null);
            before.appendAggregateEvent(event("old, version ignored"), order, null, COMMAND, 0L);

            PostgresItSupport.initializer(ds, true).initializeDatabase();

            assertThat(pg.queryForObject("select count(*) from pg_index i join pg_class c on c.oid = i.indexrelid "
                    + "where c.relname = ? and i.indisvalid and i.indisunique", Integer.class,
                    EventStore.AGGREGATE_SEQ_INDEX)).isEqualTo(1);
            var after = store(ds, mode, 2);
            assertThat(after.fenceSupported()).isTrue();
            var story = story(after, order);
            assertThat(story.aggregateVersion()).isZero();
            assertThat(story.story().events()).hasSize(2);
            after.appendAggregateEvent(event("new"), order, null, COMMAND, 0L);
            assertThatThrownBy(() -> after.appendAggregateEvent(event("fork"), order, null, COMMAND, 0L))
                    .isInstanceOf(BrokerEventStore.AggregateFenceException.class);
        }
    }
}
