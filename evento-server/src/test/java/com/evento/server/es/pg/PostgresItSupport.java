package com.evento.server.es.pg;

import com.evento.server.config.AdvisoryLockedSchemaInitializer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.core.io.DefaultResourceLoader;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * Shared plumbing for the evento-server real-Postgres ITs: one container per test class,
 * a fresh database per scenario, Hikari pools and the production schema initializer.
 */
final class PostgresItSupport {

    private PostgresItSupport() {
    }

    static PostgreSQLContainer newContainer() {
        // track_commit_timestamp lets the CPES IT check commit order against sequence order.
        return new PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("evento")
                .withUsername("evento")
                .withPassword("evento")
                .withCommand("postgres", "-c", "fsync=off", "-c", "track_commit_timestamp=on",
                        "-c", "max_connections=300");
    }

    static String jdbcUrl(PostgreSQLContainer pg, String database) {
        return "jdbc:postgresql://" + pg.getHost() + ":" + pg.getMappedPort(5432) + "/" + database;
    }

    static void createDatabase(PostgreSQLContainer pg, String database) throws SQLException {
        try (var c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             var s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
            s.execute("CREATE DATABASE " + database);
        }
    }

    static HikariDataSource dataSource(PostgreSQLContainer pg, String jdbcUrl, int poolSize) {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(pg.getUsername());
        cfg.setPassword(pg.getPassword());
        cfg.setMaximumPoolSize(poolSize);
        cfg.setMinimumIdle(0);
        return new HikariDataSource(cfg);
    }

    static AdvisoryLockedSchemaInitializer initializer(DataSource ds, boolean enforcePrimaryKey) {
        return initializer(ds, enforcePrimaryKey, DatabaseInitializationMode.ALWAYS);
    }

    static AdvisoryLockedSchemaInitializer initializer(DataSource ds, boolean enforcePrimaryKey,
                                                       DatabaseInitializationMode mode) {
        var settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of("classpath:schema.sql"));
        settings.setMode(mode);
        var init = new AdvisoryLockedSchemaInitializer(ds, settings, enforcePrimaryKey);
        init.setResourceLoader(new DefaultResourceLoader());
        return init;
    }
}
