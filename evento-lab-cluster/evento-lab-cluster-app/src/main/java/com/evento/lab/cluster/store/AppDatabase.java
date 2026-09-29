package com.evento.lab.cluster.store;

import com.evento.consumer.state.store.jdbc.FlywayMigrator;
import com.evento.consumer.state.store.jdbc.SqlDialect;
import com.evento.lab.cluster.config.LabSettings;
import com.evento.lab.cluster.config.Regions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The app-data database: consumer state (JDBC stores, never in-memory, L5),
 * the read models and the side-effect tables the invariants look at.
 *
 * <p>Every JVM of the lab runs this at boot, a dozen at once on a cold start.
 * Concurrent {@code CREATE TABLE IF NOT EXISTS} in Postgres can still fail
 * with a {@code pg_type} unique violation, so the whole initialisation runs
 * under one session-level advisory lock.
 */
public final class AppDatabase implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AppDatabase.class);
    private static final long SCHEMA_LOCK_KEY = 0x6576656e746f4cL; // "eventoL"

    private final HikariDataSource dataSource;

    public AppDatabase(LabSettings settings) {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(settings.appDbUrl());
        cfg.setUsername(settings.appDbUser());
        cfg.setPassword(settings.appDbPassword());
        // Each consumer engine pins one connection for its whole cycle
        // (JdbcConsumerLock), so the pool must exceed the number of engines.
        cfg.setMaximumPoolSize(Math.max(6, settings.appDbPoolSize()));
        cfg.setMinimumIdle(2);
        cfg.setConnectionTimeout(10_000);
        cfg.setValidationTimeout(3_000);
        cfg.setPoolName("app-db");
        this.dataSource = new HikariDataSource(cfg);
    }

    public DataSource dataSource() {
        return dataSource;
    }

    /** Flyway for the consumer-state schema, then the lab's own tables, serialised cluster-wide. */
    public void initialise() throws SQLException {
        try (Connection lockCon = dataSource.getConnection()) {
            try (var st = lockCon.createStatement()) {
                st.execute("SELECT pg_advisory_lock(" + SCHEMA_LOCK_KEY + ")");
            }
            try {
                FlywayMigrator.migrate(dataSource, SqlDialect.POSTGRES);
                try (var st = lockCon.createStatement()) {
                    for (var ddl : ddl()) {
                        st.execute(ddl);
                    }
                }
                log.info("event=app_schema_ready tables={}", tableNames());
            } finally {
                try (var st = lockCon.createStatement()) {
                    st.execute("SELECT pg_advisory_unlock(" + SCHEMA_LOCK_KEY + ")");
                }
            }
        }
    }

    public static List<String> tableNames() {
        var names = new ArrayList<String>();
        names.add(Regions.GLOBAL_TABLE);
        for (var r : Regions.ALL) names.add(Regions.regionalTable(r));
        names.add("observed_events");
        names.add("payments");
        return names;
    }

    static List<String> ddl() {
        var out = new ArrayList<String>();
        out.add(OrderViewStore.createTableSql(Regions.GLOBAL_TABLE));
        for (var r : Regions.ALL) {
            out.add(OrderViewStore.createTableSql(Regions.regionalTable(r)));
        }
        out.add("""
                CREATE TABLE IF NOT EXISTS observed_events (
                    event_seq       BIGINT       PRIMARY KEY,
                    event_name      VARCHAR(100) NOT NULL,
                    order_id        VARCHAR(200),
                    context         VARCHAR(100),
                    first_observer  VARCHAR(200),
                    last_observer   VARCHAR(200),
                    deliveries      INT          NOT NULL DEFAULT 1,
                    first_seen      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                    last_seen       TIMESTAMPTZ  NOT NULL DEFAULT now()
                )""");
        out.add("""
                CREATE TABLE IF NOT EXISTS payments (
                    order_id        VARCHAR(200) PRIMARY KEY,
                    payment_id      VARCHAR(200) NOT NULL,
                    context         VARCHAR(100),
                    attempts        INT          NOT NULL DEFAULT 1,
                    first_handler   VARCHAR(200),
                    last_handler    VARCHAR(200),
                    first_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
                    last_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
                )""");
        return out;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
