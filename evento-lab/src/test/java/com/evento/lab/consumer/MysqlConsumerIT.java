package com.evento.lab.consumer;

import com.evento.consumer.state.store.jdbc.SqlDialect;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs {@link AbstractJdbcConsumerIT} against a Testcontainers-managed MySQL 8.4 instance.
 *
 * <p>Enable with {@code EVENTO_RUN_JDBC_IT=true} (requires Docker).
 */
@Testcontainers
@EnabledIfEnvironmentVariable(
        named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class MysqlConsumerIT extends AbstractJdbcConsumerIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("evento_lab")
            .withUsername("lab")
            .withPassword("lab");

    @Override protected String jdbcUrl()         { return MYSQL.getJdbcUrl(); }
    @Override protected String username()         { return MYSQL.getUsername(); }
    @Override protected String password()         { return MYSQL.getPassword(); }
    @Override protected String driverClassName()  { return MYSQL.getDriverClassName(); }
    @Override protected SqlDialect dialect()      { return SqlDialect.MYSQL; }
}
