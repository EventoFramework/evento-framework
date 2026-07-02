package com.evento.consumer.state.store.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the baseline-skips-V1 bug: when the target schema already
 * holds the application's own tables (non-empty), {@link FlywayMigrator} used to
 * baseline at Flyway's default version 1 and silently skip
 * {@code V1__init_v2_consumer_state.sql} — leaving the {@code evento_v2_*} tables
 * absent while reporting "no migration necessary". The migrator now baselines at
 * version 0 so V1 always runs. This test reproduces the exact scenario: a
 * non-empty schema, then migrate, then assert the consumer-state table exists and
 * is usable.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class FlywayMigratorNonEmptySchemaIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("app_db")
            .withUsername("evento")
            .withPassword("evento");

    @Test
    void migrate_creates_consumer_state_tables_even_when_schema_already_has_app_tables() throws Exception {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(POSTGRES.getJdbcUrl());
        cfg.setUsername(POSTGRES.getUsername());
        cfg.setPassword(POSTGRES.getPassword());
        cfg.setDriverClassName(POSTGRES.getDriverClassName());
        try (HikariDataSource ds = new HikariDataSource(cfg)) {
            // Simulate the app's own tables already present in `public` — this is
            // what triggers Flyway to baseline the (non-empty) schema.
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("CREATE TABLE app_orders (id BIGINT PRIMARY KEY, status VARCHAR(32))");
            }

            FlywayMigrator.migrate(ds, SqlDialect.POSTGRES);

            // The consumer-state table must now exist and be queryable.
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                boolean exists;
                try (ResultSet rs = s.executeQuery(
                        "SELECT to_regclass('public.evento_v2_consumer_state') IS NOT NULL")) {
                    rs.next();
                    exists = rs.getBoolean(1);
                }
                assertThat(exists)
                        .as("evento_v2_consumer_state must be created even when the schema is non-empty")
                        .isTrue();
                // And a real read against it must not throw (the symptom was
                // "relation evento_v2_consumer_state does not exist").
                try (ResultSet rs = s.executeQuery("SELECT count(*) FROM evento_v2_consumer_state")) {
                    rs.next();
                    assertThat(rs.getLong(1)).isZero();
                }
            }
        }
    }
}
