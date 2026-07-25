package com.evento.consumer.state.store.jdbc;

import com.evento.common.messaging.consumer.ConsumerExecutors;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Answers the one operational question Phase 1 left open: does a
 * {@code ConsumerExecutors.virtual(...)} executor actually deliver its capacity when the
 * handlers hold a real JDBC transaction, or does something in the driver / pool collapse
 * the concurrency?
 *
 * <p>The historical worry is carrier-thread pinning: a virtual thread that blocks inside a
 * {@code synchronized} block used to pin its carrier, so a pool of transactional handlers
 * could degrade to roughly the core count. Three things should make that a non-issue here —
 * JDK 24's JEP 491 removed monitor-based pinning outright, and pgjdbc and HikariCP both
 * moved from {@code synchronized} to {@code ReentrantLock} — but "should" is not
 * "measured", and the consequence of being wrong (an executor silently delivering a
 * fraction of its configured capacity) is invisible in production.
 *
 * <p>So this measures the operational property directly rather than the mechanism:
 * {@link #CAPACITY} handlers, each holding a pooled connection through a server-side sleep,
 * must genuinely overlap. Deliberately set above the core count — that is where pinning
 * would have shown up.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class VirtualThreadJdbcConcurrencyIT {

    /** Above any plausible core count, so pinning could not be masked by having enough carriers. */
    private static final int CAPACITY = 32;
    private static final double SLEEP_SECONDS = 0.25;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("evento_v2")
            .withUsername("evento")
            .withPassword("evento")
            // Must exceed the pool, or the database becomes the limit instead.
            .withCommand("postgres", "-c", "max_connections=200");

    private HikariDataSource dataSource;

    @BeforeAll
    void setUp() {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(POSTGRES.getJdbcUrl());
        cfg.setUsername(POSTGRES.getUsername());
        cfg.setPassword(POSTGRES.getPassword());
        cfg.setDriverClassName(POSTGRES.getDriverClassName());
        // One connection per concurrent handler — the sizing rule from ARCHITECTURE §12.
        cfg.setMaximumPoolSize(CAPACITY);
        cfg.setConnectionTimeout(30_000);
        dataSource = new HikariDataSource(cfg);
    }

    @AfterAll
    void tearDown() {
        if (dataSource != null) dataSource.close();
    }

    /**
     * Hikari opens connections lazily, and establishing 32 of them costs far more than the
     * per-handler sleep. Without pre-warming, the first handlers finish before the last
     * ones even hold a connection and the measured peak understates the real concurrency.
     */
    private void prewarmPool() throws Exception {
        var conns = new java.util.ArrayList<Connection>();
        try {
            for (int i = 0; i < CAPACITY; i++) conns.add(dataSource.getConnection());
        } finally {
            for (var c : conns) c.close();
        }
    }

    @Test
    void transactionalHandlersOnVirtualThreadsReachFullExecutorCapacity() throws Exception {
        System.out.printf("availableProcessors=%d%n", Runtime.getRuntime().availableProcessors());
        prewarmPool();
        var concurrent = new AtomicInteger();
        var peak = new AtomicInteger();
        var failures = new AtomicInteger();
        var done = new CountDownLatch(CAPACITY);

        try (var executor = ConsumerExecutors.virtual("tx-writer", CAPACITY)) {
            var startedAt = System.nanoTime();

            for (int i = 0; i < CAPACITY; i++) {
                var admitted = executor.submit(() -> {
                    try (Connection c = dataSource.getConnection()) {
                        c.setAutoCommit(false);
                        int now = concurrent.incrementAndGet();
                        peak.updateAndGet(p -> Math.max(p, now));
                        try (var st = c.prepareStatement("SELECT pg_sleep(?)")) {
                            st.setDouble(1, SLEEP_SECONDS);
                            st.execute();
                        }
                        c.commit();
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        concurrent.decrementAndGet();
                        done.countDown();
                    }
                }, Duration.ofSeconds(30));
                assertThat(admitted).as("submission %s should be admitted", i).isPresent();
            }

            assertThat(done.await(2, TimeUnit.MINUTES)).isTrue();
            var elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            assertThat(failures.get()).as("no handler should fail").isZero();
            assertThat(peak.get())
                    .as("all %s transactional handlers must overlap; a lower peak means the "
                            + "executor is not delivering its configured capacity", CAPACITY)
                    .isEqualTo(CAPACITY);

            // Serially this is CAPACITY * SLEEP_SECONDS (8 s); fully overlapped it is one
            // sleep plus connection setup. A generous ceiling — the point is to catch an
            // order-of-magnitude collapse, not to benchmark.
            assertThat(elapsed)
                    .as("elapsed %s should be far below the serial %.1f s", elapsed,
                            CAPACITY * SLEEP_SECONDS)
                    .isLessThan(Duration.ofSeconds(4));

            System.out.printf("virtual-thread JDBC concurrency: peak=%d/%d elapsed=%d ms "
                            + "(serial would be %.0f ms)%n",
                    peak.get(), CAPACITY, elapsed.toMillis(), CAPACITY * SLEEP_SECONDS * 1000);
        }
    }

    /**
     * The counterpart failure mode, and the more likely one in practice: an executor sized
     * above its connection pool. The handlers do not deadlock — they time out acquiring a
     * connection, which the consumer classifies as transient.
     */
    @Test
    void executorWiderThanThePoolStarvesOnConnectionAcquisition() throws Exception {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(POSTGRES.getJdbcUrl());
        cfg.setUsername(POSTGRES.getUsername());
        cfg.setPassword(POSTGRES.getPassword());
        cfg.setDriverClassName(POSTGRES.getDriverClassName());
        cfg.setMaximumPoolSize(2);
        cfg.setConnectionTimeout(750);

        var acquisitionFailures = new AtomicInteger();
        var done = new CountDownLatch(8);

        try (var pool = new HikariDataSource(cfg);
             var executor = ConsumerExecutors.virtual("oversized", 8)) {

            for (int i = 0; i < 8; i++) {
                executor.submit(() -> {
                    try (Connection c = pool.getConnection()) {
                        c.setAutoCommit(false);
                        try (var st = c.prepareStatement("SELECT pg_sleep(?)")) {
                            st.setDouble(1, 1.0);
                            st.execute();
                        }
                        c.commit();
                    } catch (Exception e) {
                        acquisitionFailures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }, Duration.ofSeconds(30));
            }

            assertThat(done.await(2, TimeUnit.MINUTES)).isTrue();
            // This is what under-sizing looks like in the logs: acquisition timeouts, not
            // lock errors — and with the default retry an async handler dead-letters on the
            // first one. Hence the sizing rule in ARCHITECTURE §12.
            assertThat(acquisitionFailures.get())
                    .as("handlers beyond the pool size must fail on acquisition")
                    .isPositive();
        }
    }
}
