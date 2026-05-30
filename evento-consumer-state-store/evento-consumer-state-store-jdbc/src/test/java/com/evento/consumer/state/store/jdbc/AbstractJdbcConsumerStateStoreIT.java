package com.evento.consumer.state.store.jdbc;

import com.evento.common.messaging.consumer.ConsumerErrorState;
import com.evento.common.messaging.consumer.ConsumerLock;
import com.evento.common.messaging.consumer.EventCheckpoint;
import com.evento.common.messaging.consumer.OptimisticLockException;
import com.evento.common.messaging.consumer.ProjectorCheckpoint;
import com.evento.common.messaging.consumer.SagaCheckpoint;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.state.SagaState;
import com.evento.common.serialization.ObjectMapperUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dialect-agnostic IT covering the full v2 SPI surface: ConsumerStateStore
 * (checkpoint + version + enable + error), ConsumerLock, SagaStateStore,
 * DeadEventQueue, DedupeStore. Concrete subclasses provide a
 * {@link DataSource} pointed at a Testcontainers-managed database plus the
 * matching {@link SqlDialect}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractJdbcConsumerStateStoreIT {

    protected HikariDataSource dataSource;
    protected JdbcConsumerStateStore store;
    protected JdbcDedupeStore dedupe;
    protected JdbcConsumerLock lock;
    protected JdbcSagaStateStore sagaStore;
    protected JdbcDeadEventQueue dlq;
    protected ObjectMapper objectMapper;

    protected abstract String jdbcUrl();
    protected abstract String username();
    protected abstract String password();
    protected abstract String driverClassName();
    protected abstract SqlDialect dialect();

    @BeforeAll
    void setupClass() {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl());
        cfg.setUsername(username());
        cfg.setPassword(password());
        cfg.setDriverClassName(driverClassName());
        cfg.setMaximumPoolSize(16);
        dataSource = new HikariDataSource(cfg);

        FlywayMigrator.migrate(dataSource, dialect());

        // Use the framework's real payload mapper — the one the consumer engines
        // hand to these stores in production. A hand-rolled mapper that merely
        // enables default typing does not reproduce the field-only visibility and
        // FAIL_ON_UNKNOWN_PROPERTIES=false config the message/state types rely on.
        objectMapper = ObjectMapperUtils.getPayloadObjectMapper();

        store = new JdbcConsumerStateStore(dataSource, dialect());
        dedupe = new JdbcDedupeStore(dataSource, dialect());
        lock = new JdbcConsumerLock(dataSource, dialect());
        sagaStore = new JdbcSagaStateStore(dataSource, dialect(), objectMapper);
        dlq = new JdbcDeadEventQueue(dataSource, dialect(), objectMapper);
    }

    @AfterAll
    void teardownClass() {
        dataSource.close();
    }

    @BeforeEach
    void truncate() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM evento_v2_consumer_state");
            s.execute("DELETE FROM evento_v2_dedupe");
            s.execute("DELETE FROM evento_v2_saga_state");
            s.execute("DELETE FROM evento_v2_dead_event");
        }
    }

    // --- ConsumerStateStore: checkpoint ------------------------------------

    @Test
    void migrationCreatesAllTables() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("SELECT count(*) FROM evento_v2_consumer_state");
            s.execute("SELECT count(*) FROM evento_v2_dedupe");
            s.execute("SELECT count(*) FROM evento_v2_saga_state");
            s.execute("SELECT count(*) FROM evento_v2_dead_event");
        }
    }

    @Test
    void firstCommitReturnsVersionOne() throws Exception {
        long v = store.commit("c1", new EventCheckpoint(10), 0L);
        assertThat(v).isEqualTo(1L);
        var read = store.read("c1").orElseThrow();
        assertThat(read.version()).isEqualTo(1L);
        assertThat(read.checkpoint()).isEqualTo(new EventCheckpoint(10));
    }

    @Test
    void subsequentCommitsRequireMatchingVersion() throws Exception {
        store.commit("c1", new EventCheckpoint(1), 0L);
        store.commit("c1", new EventCheckpoint(2), 1L);
        assertThatThrownBy(() -> store.commit("c1", new EventCheckpoint(3), 1L))
                .isInstanceOf(OptimisticLockException.class);
        assertThat(store.read("c1").orElseThrow().version()).isEqualTo(2L);
    }

    @Test
    void allCheckpointKindsRoundTrip() throws Exception {
        store.commit("evt", new EventCheckpoint(10), 0L);
        store.commit("saga", new SagaCheckpoint(20), 0L);
        store.commit("proj", new ProjectorCheckpoint(30), 0L);
        assertThat(store.read("evt").orElseThrow().checkpoint()).isEqualTo(new EventCheckpoint(10));
        assertThat(store.read("saga").orElseThrow().checkpoint()).isEqualTo(new SagaCheckpoint(20));
        assertThat(store.read("proj").orElseThrow().checkpoint()).isEqualTo(new ProjectorCheckpoint(30));
    }

    @Test
    void deleteAndListConsumers() throws Exception {
        store.commit("a", new EventCheckpoint(1), 0L);
        store.commit("b", new SagaCheckpoint(2), 0L);
        try (var s = store.listConsumers()) {
            assertThat(s).containsExactlyInAnyOrder("a", "b");
        }
        store.delete("a");
        try (var s = store.listConsumers()) {
            assertThat(s).containsExactlyInAnyOrder("b");
        }
    }

    @Test
    void concurrentUpdatesHaveExactlyOneWinnerPerVersion() throws Exception {
        store.commit("c1", new EventCheckpoint(0), 0L);
        int contenders = 16;
        try (ExecutorService pool = Executors.newFixedThreadPool(contenders)) {
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger conflicts = new AtomicInteger();
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                final int payload = i + 1;
                futures.add(pool.submit(() -> {
                    try {
                        store.commit("c1", new EventCheckpoint(payload), 1L);
                        successes.incrementAndGet();
                    } catch (OptimisticLockException e) {
                        conflicts.incrementAndGet();
                    }
                }));
            }
            for (Future<?> f : futures) f.get(15, TimeUnit.SECONDS);
            assertThat(successes.get()).isEqualTo(1);
            assertThat(conflicts.get()).isEqualTo(contenders - 1);
        }
    }

    // --- ConsumerStateStore: enabled + errors ------------------------------

    @Test
    void enabledDefaultsTrueAndRoundTrips() {
        assertThat(store.isEnabled("c1")).isTrue();
        store.setEnabled("c1", false);
        assertThat(store.isEnabled("c1")).isFalse();
        store.setEnabled("c1", true);
        assertThat(store.isEnabled("c1")).isTrue();
    }

    @Test
    void errorStateStartsHealthyAndAccumulates() {
        assertThat(store.getErrorState("c1")).isEqualTo(ConsumerErrorState.healthy());

        store.setLastError("c1", new RuntimeException("first"));
        var e1 = store.getErrorState("c1");
        assertThat(e1.inError()).isTrue();
        assertThat(e1.errorCount()).isEqualTo(1L);
        assertThat(e1.errorStartAt()).isNotNull();
        assertThat(e1.errorMessage()).contains("first");

        store.setLastError("c1", new RuntimeException("second"));
        var e2 = store.getErrorState("c1");
        assertThat(e2.errorCount()).isEqualTo(2L);
        assertThat(e2.errorStartAt()).isEqualTo(e1.errorStartAt());
    }

    @Test
    void successfulCommitClearsError() throws Exception {
        store.setLastError("c1", new RuntimeException("boom"));
        assertThat(store.getErrorState("c1").inError()).isTrue();
        store.commit("c1", new EventCheckpoint(1), 0L);
        assertThat(store.getErrorState("c1")).isEqualTo(ConsumerErrorState.healthy());
    }

    // --- ConsumerLock -------------------------------------------------------

    @Test
    void lockExcludesSecondCallerUntilReleased() {
        Optional<ConsumerLock.LockHandle> first = lock.tryAcquire("hot");
        assertThat(first).isPresent();
        try {
            assertThat(lock.tryAcquire("hot")).isEmpty();
        } finally {
            first.get().close();
        }
        assertThat(lock.tryAcquire("hot")).isPresent().get().satisfies(h -> h.close());
    }

    @Test
    void lockIsPerConsumerId() {
        var a = lock.tryAcquire("a").orElseThrow();
        var b = lock.tryAcquire("b").orElseThrow();
        a.close();
        b.close();
    }

    @Test
    void lockHandleCloseIsIdempotent() {
        var h = lock.tryAcquire("c").orElseThrow();
        h.close();
        h.close(); // must not throw
        // and we can re-acquire afterwards
        var h2 = lock.tryAcquire("c").orElseThrow();
        h2.close();
    }

    // --- SagaStateStore -----------------------------------------------------

    @Test
    void sagaInsertReadByAssociation() {
        var s = new TestSaga();
        s.setAssociation("orderId", "ord-1");
        long id = sagaStore.insert("OrderSaga", s);

        var found = sagaStore.findByAssociation("OrderSaga", "orderId", "ord-1").orElseThrow();
        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getState().getAssociation("orderId")).isEqualTo("ord-1");
    }

    @Test
    void sagaFindByAssociationMissingReturnsEmpty() {
        assertThat(sagaStore.findByAssociation("OrderSaga", "orderId", "ord-x")).isEmpty();
    }

    @Test
    void sagaUpdateReplacesAssociationsAndState() {
        var s1 = new TestSaga();
        s1.setAssociation("orderId", "ord-1");
        long id = sagaStore.insert("OrderSaga", s1);

        var s2 = new TestSaga();
        s2.setAssociation("orderId", "ord-1");
        s2.setAssociation("shipmentId", "ship-1");
        sagaStore.update(id, s2);

        var byShipment = sagaStore.findByAssociation("OrderSaga", "shipmentId", "ship-1").orElseThrow();
        assertThat(byShipment.getId()).isEqualTo(id);
    }

    @Test
    void sagaDeleteRemovesInstance() {
        var s = new TestSaga();
        s.setAssociation("k", "v");
        long id = sagaStore.insert("OrderSaga", s);
        sagaStore.delete(id);
        assertThat(sagaStore.findByAssociation("OrderSaga", "k", "v")).isEmpty();
        assertThat(sagaStore.findAll("OrderSaga")).isEmpty();
    }

    // --- DeadEventQueue -----------------------------------------------------

    @Test
    void deadEventAddAndGetAll() {
        var e = makeEvent(10L, "OrderPlaced");
        dlq.add("c-A", e, new RuntimeException("boom"));
        var all = dlq.getAll("c-A");
        assertThat(all).hasSize(1);
        var first = all.iterator().next();
        assertThat(first.getEventName()).isEqualTo("OrderPlaced");
        assertThat(first.getException()).isInstanceOf(ExceptionWrapper.class);
        assertThat(first.isRetry()).isFalse();
    }

    @Test
    void deadEventRetryFlagFlips() {
        var e = makeEvent(10L, "OrderPlaced");
        dlq.add("c1", e, new RuntimeException("boom"));
        assertThat(dlq.getRetriable("c1")).isEmpty();
        dlq.setRetry("c1", 10L, true);
        assertThat(dlq.getRetriable("c1")).hasSize(1);
    }

    @Test
    void deadEventAddIsIdempotentUpsert() {
        var e = makeEvent(10L, "OrderPlaced");
        dlq.add("c1", e, new RuntimeException("first"));
        dlq.setRetry("c1", 10L, true);
        dlq.add("c1", e, new RuntimeException("second")); // upsert
        assertThat(dlq.getAll("c1")).hasSize(1);
        // The upsert resets retry=false.
        assertThat(dlq.getRetriable("c1")).isEmpty();
    }

    @Test
    void deadEventRemoveDropsRow() {
        var e = makeEvent(10L, "OrderPlaced");
        dlq.add("c1", e, new RuntimeException("boom"));
        dlq.remove("c1", e);
        assertThat(dlq.getAll("c1")).isEmpty();
    }

    // --- DedupeStore --------------------------------------------------------

    @Test
    void dedupeFirstClaimWinsSecondLoses() {
        assertThat(dedupe.tryClaim("c1", "e1")).isTrue();
        assertThat(dedupe.tryClaim("c1", "e1")).isFalse();
    }

    @Test
    void dedupeSweepRemovesOldEntriesOnly() throws Exception {
        dedupe.tryClaim("c1", "old");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("UPDATE evento_v2_dedupe SET claimed_at = '2000-01-01 00:00:00' WHERE event_id = 'old'");
        }
        dedupe.tryClaim("c1", "new");
        int removed = dedupe.sweepBefore(Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(removed).isEqualTo(1);
        assertThat(dedupe.tryClaim("c1", "old")).isTrue();
        assertThat(dedupe.tryClaim("c1", "new")).isFalse();
    }

    // --- helpers ------------------------------------------------------------

    // Non-final on purpose: NON_FINAL default typing only writes the type wrapper
    // for non-final types, and these saga states are read back via the SagaState
    // base type. Real saga states are likewise non-final.
    static class TestSaga extends SagaState {}

    static PublishedEvent makeEvent(long seq, String name) {
        var msg = new DomainEventMessage();
        msg.setContext("ctx");
        var pe = new PublishedEvent();
        pe.setEventSequenceNumber(seq);
        pe.setEventName(name);
        pe.setAggregateId("aggr-" + seq);
        pe.setEventMessage(msg);
        pe.setCreatedAt(System.currentTimeMillis());
        return pe;
    }
}
