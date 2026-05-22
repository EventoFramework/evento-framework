package com.evento.lab.consumer;

import com.evento.consumer.state.store.jdbc.v2.SqlDialect;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs {@link AbstractJdbcConsumerIT} against a Testcontainers-managed Postgres 16 instance.
 *
 * <p>Enable with {@code EVENTO_RUN_JDBC_IT=true} (requires Docker).
 */
@Testcontainers
@EnabledIfEnvironmentVariable(
        named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class PostgresConsumerIT extends AbstractJdbcConsumerIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("evento_lab")
            .withUsername("lab")
            .withPassword("lab");

    @Override protected String jdbcUrl()         { return PG.getJdbcUrl(); }
    @Override protected String username()         { return PG.getUsername(); }
    @Override protected String password()         { return PG.getPassword(); }
    @Override protected String driverClassName()  { return PG.getDriverClassName(); }
    @Override protected SqlDialect dialect()      { return SqlDialect.POSTGRES; }
}
