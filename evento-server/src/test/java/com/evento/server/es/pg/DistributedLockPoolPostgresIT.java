package com.evento.server.es.pg;

import com.evento.common.utils.PgDistributedLock;
import com.evento.server.config.DistributedLockConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The server's lock wiring: the {@link PgDistributedLock} bean pins its connections on a pool of
 * its own, derived from the application pool, so held locks never occupy application
 * connections, and the pool is closed with the context.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class DistributedLockPoolPostgresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgresItSupport.newContainer();

    @Test
    void heldLocksLiveOnADedicatedPoolClosedWithTheContext() throws Exception {
        try (var application = PostgresItSupport.dataSource(POSTGRES, POSTGRES.getJdbcUrl(), 2)) {
            HikariDataSource lockPool;
            try (var ctx = new AnnotationConfigApplicationContext()) {
                // Owned by the test, not the context (no inferred close()).
                ctx.registerBean(DataSource.class, () -> application, bd -> bd.setDestroyMethodName(""));
                ctx.register(DistributedLockConfiguration.class);
                ctx.refresh();

                var pool = ctx.getBean(DistributedLockConfiguration.LockConnectionPool.class);
                assertThat(pool.dataSource()).isInstanceOf(HikariDataSource.class).isNotSameAs(application);
                lockPool = (HikariDataSource) pool.dataSource();
                assertThat(lockPool.getMaximumPoolSize()).isEqualTo(16);
                assertThat(lockPool.getJdbcUrl()).isEqualTo(application.getJdbcUrl());

                var lock = ctx.getBean(PgDistributedLock.class);
                // More held locks than the application pool has connections.
                for (int i = 0; i < 4; i++) lock.acquire("pool-it-" + i);
                try {
                    assertThat(lockPool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(4);
                    assertThat(application.getHikariPoolMXBean().getActiveConnections()).isZero();
                    try (var c = application.getConnection()) {
                        assertThat(c.isValid(1)).as("the application pool is still free for work").isTrue();
                    }
                } finally {
                    for (int i = 0; i < 4; i++) lock.release("pool-it-" + i);
                }
            }
            assertThat(lockPool.isClosed()).isTrue();
            assertThat(application.isClosed()).as("the application pool is not the lock config's to close").isFalse();
        }
    }
}
