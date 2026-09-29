package com.evento.consumer.state.store.jdbc;

import com.evento.common.utils.LockAcquisitionTimeoutException;
import com.evento.common.utils.LockLostException;
import com.evento.common.utils.PgDistributedLock;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PgDistributedLock} against a real Postgres.
 *
 * <ul>
 *   <li>SQL-injection regression: the lock key on the command path is caller-controlled
 *       ({@code lockId}/{@code aggregateId}); it used to be concatenated into the SQL.</li>
 *   <li>G3: each held lock owns its own connection and waiting is bounded, so a waiter on a key
 *       held by another server never blocks other keys or releases; a lock whose session died is
 *       reported ({@link LockLostException}) instead of being silently treated as held.</li>
 * </ul>
 * "Another server" is simulated by a plain JDBC connection holding the advisory lock.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class PgDistributedLockIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("evento_v2")
            .withUsername("evento")
            .withPassword("evento");

    private HikariDataSource dataSource;

    @BeforeAll
    void setup() {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(POSTGRES.getJdbcUrl());
        cfg.setUsername(POSTGRES.getUsername());
        cfg.setPassword(POSTGRES.getPassword());
        cfg.setDriverClassName(POSTGRES.getDriverClassName());
        cfg.setMaximumPoolSize(8);
        dataSource = new HikariDataSource(cfg);
    }

    @AfterAll
    void teardown() {
        if (dataSource != null) dataSource.close();
    }

    @AfterEach
    void everyConnectionWentBackToThePool() {
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections())
                .as("released and failed locks return their connection")
                .isZero();
    }

    private Connection otherServer() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static boolean pgTryLock(Connection c, String key) throws SQLException {
        try (var ps = c.prepareStatement("SELECT pg_try_advisory_lock(hashtext(?))")) {
            ps.setString(1, key);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private static void pgUnlock(Connection c, String key) throws SQLException {
        try (var ps = c.prepareStatement("SELECT pg_advisory_unlock(hashtext(?))")) {
            ps.setString(1, key);
            ps.execute();
        }
    }

    @Test
    void acquiresAndReleasesWithQuoteBearingKey() {
        var lock = new PgDistributedLock(dataSource);
        // A key that would have terminated the string literal in the old concatenated SQL.
        String hostileKey = "order'); DROP TABLE evento_x; --";

        assertThatCode(() -> {
            assertThat(lock.tryAcquire(hostileKey)).isTrue();
            lock.release(hostileKey);
        }).doesNotThrowAnyException();
    }

    @Test
    void mutualExclusionHoldsForQuoteBearingKey() {
        var lock = new PgDistributedLock(dataSource);
        String key = "agg-'-\"-%s";

        assertThat(lock.tryAcquire(key)).isTrue();
        try {
            // Same key, same JVM: the local semaphore alone already denies the re-entrant attempt,
            // proving the parameterized key is handled consistently end-to-end.
            assertThat(lock.tryAcquire(key)).isFalse();
        } finally {
            lock.release(key);
        }
    }

    @Test
    void tryAcquireFailsWhileAnotherServerHoldsTheKey() throws Exception {
        var lock = new PgDistributedLock(dataSource);
        try (var other = otherServer()) {
            assertThat(pgTryLock(other, "held-elsewhere")).isTrue();
            assertThat(lock.tryAcquire("held-elsewhere")).isFalse();
            pgUnlock(other, "held-elsewhere");
            assertThat(lock.tryAcquire("held-elsewhere")).isTrue();
            lock.release("held-elsewhere");
        }
    }

    @Test
    @Timeout(60)
    void aWaiterOnAContendedKeyDoesNotBlockOtherKeysOrReleases() throws Exception {
        var lock = new PgDistributedLock(dataSource, Duration.ofSeconds(30));
        try (var other = otherServer()) {
            lock.acquire("held-here");
            assertThat(pgTryLock(other, "contended")).isTrue();

            // Before G3 this thread sat in pg_advisory_lock on the instance's only connection,
            // and every acquire and release below queued behind it.
            var waiter = CompletableFuture.runAsync(() -> lock.acquire("contended"));
            Thread.sleep(300);
            assertThat(waiter).isNotDone();

            long started = System.nanoTime();
            lock.acquire("unrelated");
            lock.release("unrelated");
            lock.release("held-here");
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertThat(elapsedMs).as("unrelated work proceeds while a waiter is parked").isLessThan(2_000);

            // Released elsewhere: the waiter gets it.
            pgUnlock(other, "contended");
            waiter.get(10, TimeUnit.SECONDS);
            // ...and actually holds it in Postgres.
            assertThat(pgTryLock(other, "contended")).isFalse();
            lock.release("contended");
            assertThat(pgTryLock(other, "contended")).isTrue();
            pgUnlock(other, "contended");
        }
    }

    @Test
    @Timeout(30)
    void waitingIsBoundedAndLeavesNothingHeld() throws Exception {
        var lock = new PgDistributedLock(dataSource, Duration.ofMillis(500));
        try (var other = otherServer()) {
            assertThat(pgTryLock(other, "stuck")).isTrue();
            long started = System.nanoTime();
            assertThatThrownBy(() -> lock.acquire("stuck"))
                    .isInstanceOf(LockAcquisitionTimeoutException.class)
                    .hasMessageContaining("stuck");
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isBetween(400L, 5_000L);

            pgUnlock(other, "stuck");
            // The local semaphore was rolled back: the key is immediately acquirable again.
            lock.acquire("stuck");
            lock.release("stuck");
        }
    }

    @Test
    @Timeout(60)
    void aLockWhoseSessionDiedIsReportedLostAndIsFreeForOthers() throws Exception {
        var lock = new PgDistributedLock(dataSource);
        try (var admin = otherServer()) {
            lock.acquire("doomed");
            lock.verifyHeld("doomed"); // alive: no-op

            // Simulate a failover / socket timeout: kill the session that owns the lock.
            int killed;
            try (var st = admin.createStatement();
                 var rs = st.executeQuery("SELECT count(pg_terminate_backend(pid)) FROM pg_locks " +
                         "WHERE locktype = 'advisory' AND granted AND pid <> pg_backend_pid()")) {
                rs.next();
                killed = rs.getInt(1);
            }
            assertThat(killed).isEqualTo(1);

            // Postgres dropped the lock: another server can take it right now...
            assertThat(pgTryLock(admin, "doomed")).isTrue();
            pgUnlock(admin, "doomed");

            // ...so the holder must not commit as if it still had it.
            assertThatThrownBy(() -> lock.verifyHeld("doomed"))
                    .isInstanceOf(LockLostException.class)
                    .extracting(e -> ((LockLostException) e).getKey()).isEqualTo("doomed");
            assertThatThrownBy(() -> lock.release("doomed")).isInstanceOf(LockLostException.class);

            // Local state was cleaned up by the failed release.
            lock.acquire("doomed");
            lock.release("doomed");
        }
    }

    @Test
    @Timeout(60)
    void lockedAreaSurfacesALockLostDuringTheCriticalSection() throws Exception {
        var lock = new PgDistributedLock(dataSource);
        try (var admin = otherServer()) {
            assertThatThrownBy(() -> lock.lockedArea("section", () -> {
                try (var st = admin.createStatement()) {
                    st.execute("SELECT pg_terminate_backend(pid) FROM pg_locks " +
                            "WHERE locktype = 'advisory' AND granted AND pid <> pg_backend_pid()");
                }
            })).isInstanceOf(LockLostException.class);
        }
    }
}
