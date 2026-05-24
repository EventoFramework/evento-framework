package com.evento.consumer.state.store.jdbc;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class PostgresJdbcConsumerStateStoreIT extends AbstractJdbcConsumerStateStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("evento_v2")
            .withUsername("evento")
            .withPassword("evento");

    @Override protected String jdbcUrl() { return POSTGRES.getJdbcUrl(); }
    @Override protected String username() { return POSTGRES.getUsername(); }
    @Override protected String password() { return POSTGRES.getPassword(); }
    @Override protected String driverClassName() { return POSTGRES.getDriverClassName(); }
    @Override protected SqlDialect dialect() { return SqlDialect.POSTGRES; }
}
