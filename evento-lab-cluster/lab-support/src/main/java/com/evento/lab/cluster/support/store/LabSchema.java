package com.evento.lab.cluster.support.store;

import com.evento.consumer.state.store.jdbc.FlywayMigrator;
import com.evento.consumer.state.store.jdbc.SqlDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

/**
 * The lab's tables. Every cell database holds the same set; the invariant
 * checker reads them all.
 *
 * <p>Several JVMs initialise one database at once on a cold start, and
 * concurrent {@code CREATE TABLE IF NOT EXISTS} in Postgres can still fail
 * with a {@code pg_type} unique violation, so every initialisation runs under
 * one session-level advisory lock.
 */
public final class LabSchema {

    private static final Logger log = LoggerFactory.getLogger(LabSchema.class);
    private static final long SCHEMA_LOCK_KEY = 0x6576656e746f4cL; // "eventoL"

    public static final String CATALOG_TABLE = "catalog_price";

    private LabSchema() {
    }

    /** Every lab table (read models, catalog, side effects). */
    public static List<String> appDdl() {
        return List.of(
                OrderViewStore.createTableSql(OrderViewStore.REGIONAL_TABLE),
                "CREATE INDEX IF NOT EXISTS order_view_context_idx ON order_view (context)",
                OrderViewStore.createTableSql(OrderViewStore.GLOBAL_TABLE),
                catalogDdl(),
                """
                CREATE TABLE IF NOT EXISTS observed_events (
                    lane            VARCHAR(100) NOT NULL,
                    event_seq       BIGINT       NOT NULL,
                    event_name      VARCHAR(100) NOT NULL,
                    order_id        VARCHAR(200),
                    context         VARCHAR(100),
                    first_observer  VARCHAR(200),
                    last_observer   VARCHAR(200),
                    deliveries      INT          NOT NULL DEFAULT 1,
                    first_seen      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                    last_seen       TIMESTAMPTZ  NOT NULL DEFAULT now(),
                    PRIMARY KEY (lane, event_seq)
                )""",
                """
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
    }

    /**
     * {@code catalog_price}: one row per SKU per regional database. {@code last_seq}
     * is the fan-out's idempotency guard, {@code applied_events} counts real
     * writes and {@code stale_skips} counts replays that were refused.
     */
    public static String catalogDdl() {
        return """
                CREATE TABLE IF NOT EXISTS catalog_price (
                    sku             VARCHAR(200) PRIMARY KEY,
                    price_cents     BIGINT       NOT NULL,
                    currency        VARCHAR(8)   NOT NULL,
                    context         VARCHAR(100) NOT NULL,
                    last_seq        BIGINT       NOT NULL,
                    applied_events  INT          NOT NULL DEFAULT 0,
                    stale_skips     INT          NOT NULL DEFAULT 0,
                    updated_by      VARCHAR(200),
                    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
                )""";
    }

    /**
     * Run {@code ddl} (and, first, the consumer-state Flyway migration when
     * {@code consumerState}) under the schema advisory lock.
     */
    public static void initialise(DataSource dataSource, boolean consumerState, List<String> ddl) throws SQLException {
        try (var lockCon = dataSource.getConnection()) {
            try (var st = lockCon.createStatement()) {
                st.execute("SELECT pg_advisory_lock(" + SCHEMA_LOCK_KEY + ")");
            }
            try {
                if (consumerState) {
                    FlywayMigrator.migrate(dataSource, SqlDialect.POSTGRES);
                }
                try (var st = lockCon.createStatement()) {
                    for (var statement : ddl) {
                        st.execute(statement);
                    }
                }
                log.info("event=lab_schema_ready consumerState={} statements={}", consumerState, ddl.size());
            } finally {
                try (var st = lockCon.createStatement()) {
                    st.execute("SELECT pg_advisory_unlock(" + SCHEMA_LOCK_KEY + ")");
                }
            }
        }
    }
}
