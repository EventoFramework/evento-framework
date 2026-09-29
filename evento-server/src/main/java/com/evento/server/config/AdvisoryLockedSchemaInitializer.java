package com.evento.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.autoconfigure.ApplicationDataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.dao.DataAccessResourceFailureException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Spring's SQL initializer ({@code schema.sql}, {@code spring.sql.init.mode=always}) run under a
 * PostgreSQL session advisory lock, followed by the event-store constraints that plain
 * {@code schema.sql} cannot express idempotently.
 *
 * <p><b>Why the lock.</b> Every server runs {@code schema.sql} on boot. When several start at
 * once (a cluster rollout, a region restart) the concurrent {@code CREATE ... IF NOT EXISTS},
 * {@code ALTER TABLE ... IF [NOT] EXISTS} and {@code CREATE OR REPLACE VIEW} statements race on
 * the catalog and one of them fails with {@code duplicate key value violates unique constraint
 * "pg_type_typname_nsp_index"} or {@code tuple concurrently updated}, aborting that server's
 * start. Holding {@link #SCHEMA_LOCK_KEY} on one dedicated connection for the whole run
 * serialises the servers; the scripts themselves run on other pooled connections, which is fine
 * because the lock only has to exclude the other servers. If a server dies mid-run its session,
 * and with it the lock, goes away.
 *
 * <p><b>Why the primary key is added here.</b> {@code es__events} historically had no primary
 * key, so nothing in the database stopped two appenders from writing the same sequence number
 * (APES Snowflake node-id collisions, or a lost CPES lock). Adding it needs a conditional
 * {@code DO} block, whose dollar-quoted body Spring's script splitter would cut at the first
 * {@code ;}; so it runs as one statement from here, inside the same lock. Existing duplicates
 * make it fail loudly with the offending count instead of silently skipping the constraint; an
 * operator who must boot anyway can set {@code evento.es.schema.enforce-primary-key=false}.
 *
 * <p>On a non-PostgreSQL database it defers to the plain initializer.
 */
public class AdvisoryLockedSchemaInitializer extends ApplicationDataSourceScriptDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryLockedSchemaInitializer.class);

    /**
     * {@code pg_advisory_lock(bigint)} key for schema initialisation ("EventoSc" in ASCII). It is
     * outside the int4 range of {@code hashtext()}, so it can never collide with a
     * {@code PgDistributedLock} key.
     */
    public static final long SCHEMA_LOCK_KEY = 0x4576656E746F5363L;

    static final String ES_EVENTS_PRIMARY_KEY_SQL = """
            DO $$
            DECLARE
                duplicated bigint;
            BEGIN
                IF NOT EXISTS (SELECT 1 FROM pg_constraint
                               WHERE conrelid = 'es__events'::regclass AND contype = 'p') THEN
                    SELECT count(*) INTO duplicated FROM (
                        SELECT event_sequence_number FROM es__events
                        GROUP BY event_sequence_number HAVING count(*) > 1) d;
                    IF duplicated > 0 THEN
                        RAISE EXCEPTION 'evento: cannot add PRIMARY KEY (event_sequence_number) to es__events: % sequence number(s) are duplicated. Two appenders wrote the same number (APES Snowflake node-id collision or a lost CPES es-lock); resolve the duplicates (SELECT event_sequence_number, count(*) FROM es__events GROUP BY 1 HAVING count(*) > 1), or boot with evento.es.schema.enforce-primary-key=false', duplicated;
                    END IF;
                    ALTER TABLE es__events ADD CONSTRAINT es__events_pkey PRIMARY KEY (event_sequence_number);
                    RAISE NOTICE 'evento: added PRIMARY KEY (event_sequence_number) to es__events';
                END IF;
            END
            $$""";

    private final boolean enforcePrimaryKey;

    public AdvisoryLockedSchemaInitializer(DataSource dataSource,
                                           DatabaseInitializationSettings settings,
                                           boolean enforcePrimaryKey) {
        super(dataSource, settings);
        this.enforcePrimaryKey = enforcePrimaryKey;
    }

    @Override
    public boolean initializeDatabase() {
        try (Connection lockConnection = getDataSource().getConnection()) {
            if (!isPostgres(lockConnection)) {
                return super.initializeDatabase();
            }
            if (!lockConnection.getAutoCommit()) lockConnection.setAutoCommit(true);
            long started = System.nanoTime();
            advisory(lockConnection, "SELECT pg_advisory_lock(?)");
            log.info("event=schema_init_lock_acquired waitMs={}", (System.nanoTime() - started) / 1_000_000);
            try {
                boolean applied = super.initializeDatabase();
                if (applied) {
                    applyEventStoreConstraints(lockConnection);
                }
                return applied;
            } finally {
                try {
                    advisory(lockConnection, "SELECT pg_advisory_unlock(?)");
                } catch (SQLException e) {
                    // Closing the connection below ends the session and drops the lock anyway.
                    log.warn("event=schema_init_unlock_failed error=\"{}\"", e.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new DataAccessResourceFailureException("Schema initialisation failed: " + e.getMessage(), e);
        }
    }

    private void applyEventStoreConstraints(Connection connection) throws SQLException {
        if (!enforcePrimaryKey) {
            log.warn("event=es_events_primary_key_skipped reason=\"evento.es.schema.enforce-primary-key=false\"");
            return;
        }
        try (var stmt = connection.createStatement()) {
            stmt.execute(ES_EVENTS_PRIMARY_KEY_SQL);
        }
    }

    private static void advisory(Connection connection, String sql) throws SQLException {
        try (var stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, SCHEMA_LOCK_KEY);
            stmt.execute();
        }
    }

    private static boolean isPostgres(Connection connection) throws SQLException {
        return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
    }
}
