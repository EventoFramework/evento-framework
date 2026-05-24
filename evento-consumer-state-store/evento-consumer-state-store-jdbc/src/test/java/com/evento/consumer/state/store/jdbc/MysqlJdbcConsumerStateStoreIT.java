package com.evento.consumer.state.store.jdbc;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class MysqlJdbcConsumerStateStoreIT extends AbstractJdbcConsumerStateStoreIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("evento_v2")
            .withUsername("evento")
            .withPassword("evento");

    @Override protected String jdbcUrl() { return MYSQL.getJdbcUrl(); }
    @Override protected String username() { return MYSQL.getUsername(); }
    @Override protected String password() { return MYSQL.getPassword(); }
    @Override protected String driverClassName() { return MYSQL.getDriverClassName(); }
    @Override protected SqlDialect dialect() { return SqlDialect.MYSQL; }
}
