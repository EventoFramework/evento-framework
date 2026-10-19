package com.evento.server.es.pg;

import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.payload.DomainEvent;
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Group commit of aggregate appends ({@code AppendBatcher}) against a real Postgres: appends that
 * arrive together are written in few transactions, a conflicting row fails only its own append,
 * and under CPES the sequence order is still the commit order (the property CPES consumers read
 * by), on two "servers" appending at once.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class GroupCommitPostgresIT {

    private static final String COMMAND = "OrderCommand";
    private static final int APPENDS = 64;

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
        PostgresItSupport.createDatabase(postgres, "group_commit_it");
        String url = PostgresItSupport.jdbcUrl(postgres, "group_commit_it");
        serverA = PostgresItSupport.dataSource(postgres, url, 8);
        serverB = PostgresItSupport.dataSource(postgres, url, 8);
        PostgresItSupport.initializer(serverA, true).initializeDatabase();
        jdbc = new JdbcTemplate(serverA);
    }

    @AfterAll
    void stop() {
        if (serverA != null) serverA.close();
        if (serverB != null) serverB.close();
        if (postgres != null) postgres.stop();
    }

    private static EventStore store(HikariDataSource ds, EventStoreMode mode, int node) throws Exception {
        var store = new EventStore(null, null, new JdbcTemplate(ds), ds, 64, 64, 150_000, 150_000, 69, mode);
        var snowflake = EventStore.class.getDeclaredField("snowflake");
        snowflake.setAccessible(true);
        snowflake.set(store, new com.evento.common.utils.Snowflake(node));
        return store;
    }

    private static DomainEventMessage event(String note) {
        return new DomainEventMessage(new OrderEvent(note));
    }

    /** Runs every task at once (released together) and returns their outcomes: a seq, or the exception. */
    private static List<Object> together(List<Callable<Long>> tasks) throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Object>>();
            for (var task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return (Object) task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            var out = new ArrayList<Object>();
            for (var f : futures) out.add(f.get(60, TimeUnit.SECONDS));
            return out;
        }
    }

    @ParameterizedTest
    @EnumSource(EventStoreMode.class)
    void appendsThatArriveTogetherShareCommits(EventStoreMode mode) throws Exception {
        try (var a = store(serverA, mode, 1); var b = store(serverB, mode, 2)) {
            var tasks = new ArrayList<Callable<Long>>();
            for (int i = 0; i < APPENDS; i++) {
                var store = i % 2 == 0 ? a : b;
                var order = "order-" + UUID.randomUUID();
                tasks.add(() -> store.appendAggregateEvent(event("created"), order, null, COMMAND, 0L));
            }
            var outcomes = together(tasks);

            assertThat(outcomes).allMatch(o -> o instanceof Long);
            assertThat(outcomes).doesNotHaveDuplicates();
            assertThat(a.batchedAppendCount() + b.batchedAppendCount()).isEqualTo(APPENDS);
            // Fewer commits than appends: the point of it.
            assertThat(a.appendBatchCount() + b.appendBatchCount()).isLessThan(APPENDS);
        }
    }

    @ParameterizedTest
    @EnumSource(EventStoreMode.class)
    void aTakenVersionFailsOnlyItsOwnAppend(EventStoreMode mode) throws Exception {
        try (var a = store(serverA, mode, 3); var b = store(serverB, mode, 4)) {
            var contested = "order-" + UUID.randomUUID();
            var tasks = new ArrayList<Callable<Long>>();
            for (int i = 0; i < 16; i++) {
                var store = i % 2 == 0 ? a : b;
                tasks.add(() -> store.appendAggregateEvent(event("contested"), contested, null, COMMAND, 0L));
                var free = "order-" + UUID.randomUUID();
                tasks.add(() -> store.appendAggregateEvent(event("free"), free, null, COMMAND, 0L));
            }
            var outcomes = together(tasks);

            long contestedStored = 0;
            for (int i = 0; i < outcomes.size(); i += 2) {
                if (outcomes.get(i) instanceof Long) {
                    contestedStored++;
                } else {
                    assertThat(outcomes.get(i)).isInstanceOf(BrokerEventStore.AggregateFenceException.class);
                }
                assertThat(outcomes.get(i + 1)).as("an append batched with a conflict").isInstanceOf(Long.class);
            }
            assertThat(contestedStored).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from es__events where aggregate_id = ?", Long.class,
                    contested)).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @EnumSource(EventStoreMode.class)
    void aStoredKeyFailsOnlyItsOwnAppend(EventStoreMode mode) throws Exception {
        try (var a = store(serverA, mode, 5); var b = store(serverB, mode, 6)) {
            var key = "key-" + UUID.randomUUID();
            var order = "order-" + UUID.randomUUID();
            var tasks = new ArrayList<Callable<Long>>();
            for (int i = 0; i < 8; i++) {
                var store = i % 2 == 0 ? a : b;
                tasks.add(() -> store.appendAggregateEvent(event("keyed"), order, key, COMMAND, 0L));
            }
            var outcomes = together(tasks);

            assertThat(outcomes.stream().filter(o -> o instanceof Long)).hasSize(1);
            assertThat(jdbc.queryForObject("select count(*) from es__events where idempotency_key = ?", Long.class,
                    key)).isEqualTo(1);
        }
    }

    /**
     * What CPES consumers rely on: a reader that has seen sequence number n never sees a smaller
     * one appear later. A transaction id is assigned at the first write, under {@code es-lock},
     * so transaction ids in sequence order must never go down; a batch shares one.
     */
    @ParameterizedTest
    @EnumSource(value = EventStoreMode.class, names = "CPES")
    void underCpesTheSequenceOrderIsTheCommitOrder(EventStoreMode mode) throws Exception {
        jdbc.update("delete from es__events");
        try (var a = store(serverA, mode, 7); var b = store(serverB, mode, 8)) {
            var tasks = new ArrayList<Callable<Long>>();
            for (int i = 0; i < 4 * APPENDS; i++) {
                var store = i % 2 == 0 ? a : b;
                var order = "order-" + UUID.randomUUID();
                tasks.add(() -> store.appendAggregateEvent(event("created"), order, null, COMMAND, 0L));
            }
            assertThat(together(tasks)).allMatch(o -> o instanceof Long);
        }
        List<Long> xids = jdbc.queryForList(
                "select xmin::text::bigint from es__events order by event_sequence_number", Long.class);
        assertThat(xids).hasSize(4 * APPENDS).isSorted();
        assertThat(xids.stream().distinct().count()).as("transactions").isLessThan(4L * APPENDS);
    }
}
