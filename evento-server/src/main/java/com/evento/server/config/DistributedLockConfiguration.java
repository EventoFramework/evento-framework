package com.evento.server.config;

import com.evento.common.utils.PgDistributedLock;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * The server's single {@link PgDistributedLock}, backed by a connection pool of its own.
 *
 * <p>A held lock pins one connection for as long as it is held; a locked aggregate command holds
 * it across the whole bundle round trip, and during it the holder needs <em>more</em>
 * connections for its work (story fetch, snapshot, event append). On the application pool those
 * two demands compete: with as many concurrent locked commands as the pool has connections
 * (the business executor allows up to {@code cpus x 8}), every connection is pinned by a holder,
 * every holder blocks waiting for a second one, and they all fail together at Hikari's
 * connection timeout. A separate pool removes that cycle: exhausting it only makes further
 * acquirers wait (bounded by the acquire timeout), while holders keep working on the
 * application pool and release.
 *
 * <p>The lock pool copies the application pool's configuration (URL, credentials, driver
 * properties such as {@code socketTimeout}/{@code tcpKeepAlive}) and overrides only its size and
 * connection timeout:
 * <ul>
 *   <li>{@code evento.lock.pool.maximum-size} (default 10): the most locks this server can hold
 *       at once. Budget {@code servers x (application pool + lock pool)} under PostgreSQL's
 *       {@code max_connections}: with the shipped 20 + 10, three servers peak at 90 connections,
 *       within PostgreSQL's default of 100.</li>
 *   <li>{@code evento.lock.pool.connection-timeout-ms} (default 1000): how long one acquire attempt
 *       waits for the pool to open a connection (database unreachable) before counting as failed
 *       and backing off. Waiting for a connection that holders have taken is bounded by the
 *       caller instead: the lock keeps one permit per pool connection.</li>
 *   <li>{@code evento.lock.acquire.timeout.ms} (default 60000): the bound on any lock wait.</li>
 * </ul>
 * When the application DataSource is not a Hikari pool (tests, exotic setups) the lock falls back
 * to it, with a warning.
 */
@Configuration(proxyBeanMethods = false)
public class DistributedLockConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockConfiguration.class);

    /** Owns the dedicated lock pool so Spring closes it after every lock user is gone. */
    public static final class LockConnectionPool implements AutoCloseable {
        private final DataSource dataSource;
        private final HikariDataSource owned;

        LockConnectionPool(DataSource dataSource, HikariDataSource owned) {
            this.dataSource = dataSource;
            this.owned = owned;
        }

        public DataSource dataSource() {
            return dataSource;
        }

        @Override
        public void close() {
            if (owned != null) owned.close();
        }
    }

    @Bean(destroyMethod = "close")
    public LockConnectionPool eventoLockConnectionPool(
            DataSource dataSource,
            @Value("${evento.lock.pool.maximum-size:10}") int maximumSize,
            @Value("${evento.lock.pool.connection-timeout-ms:1000}") long connectionTimeoutMs) {
        if (!(dataSource instanceof HikariDataSource application)) {
            log.warn("event=lock_pool_shared reason=\"application DataSource is {}, not Hikari\" " +
                    "held locks share the application pool", dataSource.getClass().getName());
            return new LockConnectionPool(dataSource, null);
        }
        var config = new HikariConfig();
        application.copyStateTo(config);
        config.setPoolName(poolName(application.getPoolName()));
        config.setMaximumPoolSize(maximumSize);
        // Idle lock connections are only warm spares for acquire attempts; keep few of them.
        config.setMinimumIdle(Math.min(2, maximumSize));
        // Hikari's floor is 250 ms.
        config.setConnectionTimeout(Math.max(250, connectionTimeoutMs));
        // Held locks are long-lived by design; they are not leaks.
        config.setLeakDetectionThreshold(0);
        log.info("event=lock_pool_created pool={} maximumSize={}", config.getPoolName(), maximumSize);
        var pool = new HikariDataSource(config);
        return new LockConnectionPool(pool, pool);
    }

    /**
     * The lock itself. Its acquire bound is a Spring property like the pool settings
     * ({@code evento.lock.acquire.timeout.ms}, env {@code EVENTO_LOCK_ACQUIRE_TIMEOUT_MS}); the
     * JVM system property of the same name is the fallback when the Spring property is unset.
     * On the dedicated pool the lock is told the pool size, so it never waits on the pool for a
     * connection that is not free ({@link PgDistributedLock#PgDistributedLock(DataSource, Duration, int)}).
     */
    @Bean
    public PgDistributedLock eventoDistributedLock(
            LockConnectionPool pool,
            @Value("${evento.lock.acquire.timeout.ms:#{null}}") Long acquireTimeoutMs) {
        long timeoutMs = acquireTimeoutMs != null ? acquireTimeoutMs
                : Long.getLong("evento.lock.acquire.timeout.ms", PgDistributedLock.DEFAULT_ACQUIRE_TIMEOUT_MS);
        int permits = pool.owned != null ? pool.owned.getMaximumPoolSize() : 0;
        log.info("event=distributed_lock_created acquireTimeoutMs={} maxConnections={}", timeoutMs, permits);
        return new PgDistributedLock(pool.dataSource(), Duration.ofMillis(timeoutMs), permits);
    }

    private static String poolName(String applicationPoolName) {
        return (applicationPoolName == null ? "evento" : applicationPoolName) + "-locks";
    }
}
