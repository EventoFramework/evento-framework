package com.evento.server.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.autoconfigure.ApplicationDataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.dao.DataAccessResourceFailureException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * The server's schema on PostgreSQL, with {@code spring.sql.init.mode=always}: Flyway migrations
 * (F4) run under a PostgreSQL session advisory lock.
 *
 * <p><b>Flyway (3.0).</b> History in {@value #HISTORY_TABLE}, migrations in
 * {@value #MIGRATIONS}: {@code V1__baseline_schema.sql} is the 3.0.0 schema (the old
 * {@code schema.sql}, idempotent); V2-V5 are the online, conditional steps described below, as Java
 * migrations ({@link V2__Catalog_component_identity}, {@link V3__Es_events_primary_key},
 * {@link V4__Es_events_idempotency_key}, {@link V5__Es_events_aggregate_fence}); every later schema
 * change is a new version. A database of an earlier server has no history: Flyway baselines it at
 * version 0 and runs V1-V5, which find what is already there and add what is missing. V2-V5 run
 * outside a transaction ({@code CREATE INDEX CONCURRENTLY}); one that failed half way (a lock
 * timeout, a duplicate) is recorded as failed, and since each of them checks the state before
 * acting, the next boot repairs the history and runs it again instead of refusing to start.
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
 * <p>The lock is taken by polling {@code pg_try_advisory_lock}, not by blocking in
 * {@code pg_advisory_lock}: a server parked inside a blocking lock call keeps a snapshot open, and
 * {@code CREATE INDEX CONCURRENTLY} (below) waits for every older snapshot to go away, so the
 * index build of the lock holder and the waiters would deadlock each other.
 *
 * <p><b>Why the primary key is added here.</b> {@code es__events} historically had no primary
 * key, so nothing in the database stopped two appenders from writing the same sequence number
 * (APES Snowflake node-id collisions, or a lost CPES lock). The key is added conditionally, which
 * {@code schema.sql} cannot express, and without locking the event store for the length of an
 * index build, because the first server to boot the new version typically does it during a
 * rolling upgrade while old servers keep appending and tailing:
 * <ol>
 *   <li>existing duplicates fail the boot loudly with the offending count (an operator who must
 *       boot anyway sets {@code evento.es.schema.enforce-primary-key=false});</li>
 *   <li>the unique index is built with {@code CREATE UNIQUE INDEX CONCURRENTLY}, which never
 *       blocks reads or appends (an invalid leftover of an interrupted build is dropped first);</li>
 *   <li>{@code ALTER TABLE ... ADD PRIMARY KEY USING INDEX} then only swaps the finished index in
 *       ({@code event_sequence_number} is already {@code NOT NULL}, so there is no scan). It needs
 *       {@code ACCESS EXCLUSIVE} for an instant; under a short {@code lock_timeout}, so a long
 *       query in flight makes it retry instead of queueing every later query behind it.</li>
 * </ol>
 *
 * <p><b>Idempotency keys (G5).</b> {@code es__events.idempotency_key} and its partial unique
 * index are added the same way, online and only when missing ({@link #applyIdempotencyKey}).
 *
 * <p><b>The aggregate fence (C1).</b> {@code es__events.aggregate_seq} and the partial unique
 * index on {@code (aggregate_id, aggregate_seq)} too ({@link #applyAggregateFence}).
 *
 * <p><b>Why the catalog migration is here.</b> Catalogs created before components were keyed per
 * bundle have {@code core__component.component_name} as primary key, referenced by
 * {@code core__handler.component_component_name} and {@code core__consumer.component_component_name}.
 * Re-keying them to {@code core__component.id} ({@code bundle_id || ':' || component_name}) is
 * conditional on the old shape and needs {@code DO}-free, ordered DDL, so it runs here, once, in
 * one transaction ({@link #migrateComponentIdentity}).
 *
 * <p><b>When Boot does not run {@code schema.sql}</b> ({@code spring.sql.init.mode=never}, a
 * schema managed outside the server) neither migration runs, since the server is then not
 * expected to change the schema. A catalog still in the old shape would make every catalog read
 * and write fail, so the boot fails instead, naming the upgrade script shipped for this case
 * ({@value #COMPONENT_ID_UPGRADE_SCRIPT}); a missing {@code es__events} primary key is only
 * warned about ({@value #ES_EVENTS_PK_UPGRADE_SCRIPT}), since the event store works without it.
 *
 * <p><b>Minimum PostgreSQL version.</b> The server needs PostgreSQL {@value #MIN_SERVER_VERSION_NUM}
 * ({@code server_version_num}) or later and refuses to boot on an older one, instead of failing
 * later on a statement the old server cannot parse.
 *
 * <p>On a non-PostgreSQL database it defers to the plain initializer ({@code schema.sql}).
 */
public class AdvisoryLockedSchemaInitializer extends ApplicationDataSourceScriptDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryLockedSchemaInitializer.class);

    /**
     * {@code pg_advisory_lock(bigint)} key for schema initialisation ("EventoSc" in ASCII). It is
     * outside the int4 range of {@code hashtext()}, so it can never collide with a
     * {@code PgDistributedLock} key.
     */
    public static final long SCHEMA_LOCK_KEY = 0x4576656E746F5363L;

    /** Flyway's history table for the server schema (the consumer state store has its own). */
    public static final String HISTORY_TABLE = "evento_es_schema_history";
    /** Where the SQL migrations are. */
    public static final String MIGRATIONS = "classpath:db/migration/evento-server";

    /** Name of the unique index built concurrently and then promoted to the primary key. */
    static final String ES_EVENTS_SEQ_INDEX = "es__events_seq_uidx";

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String LOCK_NOT_AVAILABLE = "55P03";
    /** How long the primary-key swap may wait for {@code ACCESS EXCLUSIVE} per attempt. */
    private static final String ATTACH_LOCK_TIMEOUT = "3s";
    private static final int ATTACH_ATTEMPTS = 20;

    /** {@code server_version_num} of the oldest supported PostgreSQL (12.0). */
    static final int MIN_SERVER_VERSION_NUM = 120000;

    /** Classpath upgrade script for externally managed schemas: the N1 catalog re-key. */
    public static final String COMPONENT_ID_UPGRADE_SCRIPT = "db/upgrade/n1-component-id.sql";
    /** Classpath upgrade script for externally managed schemas: the es__events primary key. */
    public static final String ES_EVENTS_PK_UPGRADE_SCRIPT = "db/upgrade/es-events-primary-key.sql";
    /** Classpath upgrade script for externally managed schemas: the idempotency key column + index (G5). */
    public static final String ES_EVENTS_IDEMPOTENCY_UPGRADE_SCRIPT = "db/upgrade/es-events-idempotency-key.sql";

    /** Classpath upgrade script for externally managed schemas: the aggregate fence column + index (C1). */
    public static final String ES_EVENTS_AGGREGATE_SEQ_UPGRADE_SCRIPT = "db/upgrade/es-events-aggregate-seq.sql";

    /** Partial unique index on {@code es__events (aggregate_id, aggregate_seq)}; the name the event store probes for. */
    public static final String ES_EVENTS_AGGREGATE_SEQ_INDEX = "es__events_aggregate_seq_uidx";

    /** Partial unique index on {@code es__events.idempotency_key}; the name the event store probes for. */
    public static final String ES_EVENTS_IDEMPOTENCY_INDEX = "es__events_idempotency_key_uidx";

    /** Claims of in-flight keyed service commands (G5); created by {@code schema.sql}. */
    public static final String IDEMPOTENCY_CLAIMS_TABLE = "es__idempotency_claims";

    private final boolean enforcePrimaryKey;
    private final DatabaseInitializationMode mode;

    public AdvisoryLockedSchemaInitializer(DataSource dataSource,
                                           DatabaseInitializationSettings settings,
                                           boolean enforcePrimaryKey) {
        super(dataSource, settings);
        this.enforcePrimaryKey = enforcePrimaryKey;
        this.mode = settings.getMode();
    }

    @Override
    public boolean initializeDatabase() {
        try (Connection lockConnection = getDataSource().getConnection()) {
            if (!isPostgres(lockConnection)) {
                return super.initializeDatabase();
            }
            if (!lockConnection.getAutoCommit()) lockConnection.setAutoCommit(true);
            checkServerVersion(lockConnection);
            long started = System.nanoTime();
            awaitSchemaLock(lockConnection);
            log.info("event=schema_init_lock_acquired waitMs={}", (System.nanoTime() - started) / 1_000_000);
            try {
                if (mode != DatabaseInitializationMode.ALWAYS) {
                    checkExternallyManagedSchema(lockConnection);
                    return false;
                }
                migrate();
                // V3-V5 run once, but what they add is checked again on every boot, as before 3.0
                // (a query or two when it is all there): the primary key skipped with
                // evento.es.schema.enforce-primary-key=false is added once the property is true again,
                // and an index or column removed by hand comes back.
                applyEventStoreConstraints(lockConnection);
                applyIdempotencyKey(lockConnection);
                applyAggregateFence(lockConnection);
                return true;
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

    /** The Flyway of the server schema; package-private for the tests. */
    Flyway flyway() {
        return Flyway.configure(getClass().getClassLoader())
                .dataSource(getDataSource())
                .locations(MIGRATIONS)
                .table(HISTORY_TABLE)
                // A database of a server before 3.0 has the tables but no history: version 0, so
                // V1 (idempotent) and V2-V5 (conditional) still run on it.
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .baselineDescription("schema of a server before 3.0")
                .placeholderReplacement(false)
                // Flyway's default lock keeps a transaction open for the whole run, and
                // CREATE INDEX CONCURRENTLY (V3-V5) waits for it to end: it would wait for itself.
                .configuration(java.util.Map.of("flyway.postgresql.transactional.lock", "false"))
                .javaMigrations(new V2__Catalog_component_identity(), new V3__Es_events_primary_key(),
                        new V4__Es_events_idempotency_key(), new V5__Es_events_aggregate_fence())
                .load();
    }

    private void migrate() {
        var flyway = flyway();
        boolean failedBefore = false;
        for (MigrationInfo info : flyway.info().all()) {
            if (info.getState() == MigrationState.FAILED) {
                // Only V2-V5 can fail half way (outside a transaction), and they redo what is missing.
                log.warn("event=schema_migration_failed_before version={} description=\"{}\" action=repair_and_retry",
                        info.getVersion(), info.getDescription());
                failedBefore = true;
            }
        }
        if (failedBefore) flyway.repair();
        var result = flyway.migrate();
        var current = flyway.info().current();
        log.info("event=schema_migrated version={} applied={} table={}",
                current == null ? null : current.getVersion(), result.migrationsExecuted, HISTORY_TABLE);
    }

    /**
     * A conditional, online step as a Flyway migration: outside a transaction (it builds indexes
     * {@code CONCURRENTLY} or manages its own), safe to run again.
     */
    private abstract class OnlineStep extends BaseJavaMigration {
        @Override
        public boolean canExecuteInTransaction() {
            return false;
        }

        @Override
        public void migrate(Context context) throws Exception {
            var connection = context.getConnection();
            if (!connection.getAutoCommit()) connection.setAutoCommit(true);
            apply(connection);
        }

        abstract void apply(Connection connection) throws SQLException;
    }

    /** The N1 catalog re-key ({@link #migrateComponentIdentity}). */
    final class V2__Catalog_component_identity extends OnlineStep {
        @Override
        void apply(Connection connection) throws SQLException {
            migrateComponentIdentity(connection);
        }
    }

    /** The {@code es__events} primary key ({@link #applyEventStoreConstraints}). */
    final class V3__Es_events_primary_key extends OnlineStep {
        @Override
        void apply(Connection connection) throws SQLException {
            applyEventStoreConstraints(connection);
        }
    }

    /** Idempotency keys, G5 ({@link #applyIdempotencyKey}). */
    final class V4__Es_events_idempotency_key extends OnlineStep {
        @Override
        void apply(Connection connection) throws SQLException {
            applyIdempotencyKey(connection);
        }
    }

    /** The aggregate fence, C1 ({@link #applyAggregateFence}). */
    final class V5__Es_events_aggregate_fence extends OnlineStep {
        @Override
        void apply(Connection connection) throws SQLException {
            applyAggregateFence(connection);
        }
    }

    /**
     * Refuses PostgreSQL older than {@link #MIN_SERVER_VERSION_NUM}. Nothing in the server has
     * been tested on one, and failing here names the problem instead of failing on the first
     * statement that uses newer syntax.
     */
    static void checkServerVersion(Connection connection) throws SQLException {
        long version = queryLong(connection, "SELECT current_setting('server_version_num')::int");
        if (version < MIN_SERVER_VERSION_NUM) {
            throw new SQLException("evento: PostgreSQL " + version / 10000 + " is not supported; "
                    + "the server needs PostgreSQL 12 or later (server_version_num >= " + MIN_SERVER_VERSION_NUM
                    + ", found " + version + ")", "0A000");
        }
    }

    /**
     * With {@code schema.sql} not run by Boot, the schema is the operator's: nothing is changed,
     * but a catalog in the pre-N1 shape fails the boot with the script to apply, and a missing
     * {@code es__events} primary key is logged.
     */
    private void checkExternallyManagedSchema(Connection connection) throws SQLException {
        if (hasOldComponentShape(connection)) {
            throw new SQLException("evento: core__component is still keyed by component_name (pre-N1 catalog) "
                    + "and spring.sql.init.mode does not let this server migrate it. Apply the upgrade script "
                    + "classpath:" + COMPONENT_ID_UPGRADE_SCRIPT + " (in evento-server.jar), or boot one server "
                    + "once with spring.sql.init.mode=always", "55000");
        }
        if (enforcePrimaryKey && tableExists(connection, "es__events")
                && queryLong(connection, "SELECT count(*) FROM pg_constraint "
                + "WHERE conrelid = 'es__events'::regclass AND contype = 'p'") == 0) {
            log.warn("event=es_events_primary_key_missing reason=\"spring.sql.init.mode is not 'always', "
                    + "so the server does not add it\" script=classpath:{}", ES_EVENTS_PK_UPGRADE_SCRIPT);
        }
        if (tableExists(connection, "es__events")
                && (!validIndexExists(connection, ES_EVENTS_IDEMPOTENCY_INDEX)
                || !columnExists(connection, "es__events", "idempotency_command")
                || !relationResolves(connection, IDEMPOTENCY_CLAIMS_TABLE))) {
            log.warn("event=es_events_idempotency_key_missing reason=\"spring.sql.init.mode is not 'always', "
                    + "so the server does not add the idempotency index, column and claims table; keyed commands "
                    + "run without exactly-once\" script=classpath:{}",
                    ES_EVENTS_IDEMPOTENCY_UPGRADE_SCRIPT);
        }
        if (tableExists(connection, "es__events")
                && (!validIndexExists(connection, ES_EVENTS_AGGREGATE_SEQ_INDEX)
                || !columnExists(connection, "es__events", "aggregate_seq"))) {
            log.warn("event=es_events_aggregate_fence_missing reason=\"spring.sql.init.mode is not 'always', "
                    + "so the server does not add the aggregate_seq column and index; aggregate events are "
                    + "appended unfenced\" script=classpath:{}", ES_EVENTS_AGGREGATE_SEQ_UPGRADE_SCRIPT);
        }
    }

    /**
     * Adds {@code es__events.idempotency_key} and its partial unique index (G5) when missing,
     * the same way as the primary key: nothing that blocks appends for longer than an instant,
     * because the first server of the new version usually does it while old servers append.
     * <ol>
     *   <li>The column is nullable without a default, so {@code ADD COLUMN} only touches the
     *       catalog; it needs {@code ACCESS EXCLUSIVE} for an instant and runs under a short
     *       {@code lock_timeout}, retried. It is skipped when present: {@code IF NOT EXISTS} alone
     *       would still take the lock on every boot.</li>
     *   <li>{@code CREATE UNIQUE INDEX CONCURRENTLY ... WHERE idempotency_key IS NOT NULL}: rows of
     *       old servers and unkeyed commands (all {@code NULL}) are outside the index, so existing
     *       data can never violate it and it stays as small as the number of keyed events. An
     *       invalid leftover of an interrupted build is dropped first.</li>
     * </ol>
     * Keys have no expiry (they live as long as their events); a retention policy is future work.
     */
    private void applyIdempotencyKey(Connection connection) throws SQLException {
        if (!columnExists(connection, "es__events", "idempotency_key")) {
            runWithLockTimeout(connection, "ALTER TABLE es__events ADD COLUMN IF NOT EXISTS idempotency_key varchar(255)",
                    "es_events_idempotency_key_column");
            log.info("event=es_events_idempotency_key_column_added");
        }
        if (!columnExists(connection, "es__events", "idempotency_command")) {
            runWithLockTimeout(connection,
                    "ALTER TABLE es__events ADD COLUMN IF NOT EXISTS idempotency_command varchar(255)",
                    "es_events_idempotency_command_column");
            log.info("event=es_events_idempotency_command_column_added");
        }
        dropInvalidIndex(connection, ES_EVENTS_IDEMPOTENCY_INDEX);
        if (validIndexExists(connection, ES_EVENTS_IDEMPOTENCY_INDEX)) return;
        long started = System.nanoTime();
        try {
            execute(connection, "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS " + ES_EVENTS_IDEMPOTENCY_INDEX
                    + " ON es__events (idempotency_key) WHERE idempotency_key IS NOT NULL");
        } catch (SQLException e) {
            dropInvalidIndex(connection, ES_EVENTS_IDEMPOTENCY_INDEX);
            throw e;
        }
        log.info("event=es_events_idempotency_index_built index={} durationMs={}",
                ES_EVENTS_IDEMPOTENCY_INDEX, (System.nanoTime() - started) / 1_000_000);
    }

    /**
     * Adds {@code es__events.aggregate_seq} and the partial unique index on
     * {@code (aggregate_id, aggregate_seq)} (C1) when missing, online, exactly like
     * {@link #applyIdempotencyKey}: a catalog-only column under a short lock timeout, then the
     * index built {@code CONCURRENTLY}. Existing rows (and later service events) have no version
     * and stay outside the index, so the build cannot fail on old data.
     */
    private void applyAggregateFence(Connection connection) throws SQLException {
        if (!columnExists(connection, "es__events", "aggregate_seq")) {
            runWithLockTimeout(connection, "ALTER TABLE es__events ADD COLUMN IF NOT EXISTS aggregate_seq bigint",
                    "es_events_aggregate_seq_column");
            log.info("event=es_events_aggregate_seq_column_added");
        }
        dropInvalidIndex(connection, ES_EVENTS_AGGREGATE_SEQ_INDEX);
        if (validIndexExists(connection, ES_EVENTS_AGGREGATE_SEQ_INDEX)) return;
        long started = System.nanoTime();
        try {
            execute(connection, "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS " + ES_EVENTS_AGGREGATE_SEQ_INDEX
                    + " ON es__events (aggregate_id, aggregate_seq) WHERE aggregate_seq IS NOT NULL");
        } catch (SQLException e) {
            dropInvalidIndex(connection, ES_EVENTS_AGGREGATE_SEQ_INDEX);
            throw e;
        }
        log.info("event=es_events_aggregate_seq_index_built index={} durationMs={}",
                ES_EVENTS_AGGREGATE_SEQ_INDEX, (System.nanoTime() - started) / 1_000_000);
    }

    /**
     * Whether {@code index} exists, valid, <em>on the {@code es__events} this server uses</em>:
     * the table {@code search_path} resolves, as every statement of the server does. Matching the
     * index name alone in {@code pg_index} also matched an index of another schema's
     * {@code es__events} (another tenant, a copy), and the server then trusted an index that
     * did not guard its own table.
     */
    private static boolean validIndexExists(Connection connection, String index) throws SQLException {
        return queryLong(connection, "SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid " +
                "WHERE i.indrelid = to_regclass('es__events') AND c.relname = '" + index + "' AND i.indisvalid") > 0;
    }

    /** Drops an INVALID {@code index} of the {@code es__events} this server uses, schema-qualified. */
    private static void dropInvalidIndex(Connection connection, String index) throws SQLException {
        String qualified;
        try (var stmt = connection.createStatement();
             var rs = stmt.executeQuery("SELECT c.oid::regclass::text FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid " +
                     "WHERE i.indrelid = to_regclass('es__events') AND c.relname = '" + index + "' AND NOT i.indisvalid")) {
            qualified = rs.next() ? rs.getString(1) : null;
        }
        if (qualified != null) {
            log.warn("event=es_events_index_invalid action=drop index={}", qualified);
            execute(connection, "DROP INDEX CONCURRENTLY IF EXISTS " + qualified);
        }
    }

    /** Whether {@code table}, as {@code search_path} resolves it, has {@code column}. */
    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        return queryLong(connection, "SELECT count(*) FROM pg_attribute WHERE attrelid = to_regclass('" + table + "') "
                + "AND attname = '" + column + "' AND NOT attisdropped") > 0;
    }

    /** Whether {@code relation} resolves through {@code search_path}. */
    private static boolean relationResolves(Connection connection, String relation) throws SQLException {
        return queryLong(connection, "SELECT count(*) WHERE to_regclass('" + relation + "') IS NOT NULL") > 0;
    }

    /** One DDL statement that needs {@code ACCESS EXCLUSIVE} for an instant, retried under a short lock timeout. */
    private static void runWithLockTimeout(Connection connection, String ddl, String what) throws SQLException {
        execute(connection, "SET lock_timeout = '" + ATTACH_LOCK_TIMEOUT + "'");
        try {
            for (int attempt = 1; ; attempt++) {
                try {
                    execute(connection, ddl);
                    return;
                } catch (SQLException e) {
                    if (!LOCK_NOT_AVAILABLE.equals(e.getSQLState()) || attempt >= ATTACH_ATTEMPTS) throw e;
                    log.warn("event={}_lock_busy attempt={} lockTimeout={}", what, attempt, ATTACH_LOCK_TIMEOUT);
                    sleep(Math.min(250L * attempt, 2_000L));
                }
            }
        } finally {
            execute(connection, "RESET lock_timeout");
        }
    }

    private static boolean hasOldComponentShape(Connection connection) throws SQLException {
        return tableExists(connection, "core__component")
                && queryLong(connection, "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = 'core__component' "
                + "AND column_name = 'id'") == 0;
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        return queryLong(connection, "SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = current_schema() AND table_name = '" + table + "'") > 0;
    }

    /**
     * Re-keys the component catalog per bundle (N1) when it still has the old shape, then makes
     * sure the lookup indexes exist. Idempotent: a catalog that already has
     * {@code core__component.id} is left alone, so every boot after the first is a no-op.
     *
     * <p>The rewrite runs in one transaction: a server that dies half-way leaves the old shape,
     * and the next boot starts over. Existing rows keep their bundle; handler and consumer rows
     * follow their component. Rows that the old keying had already mixed up (one row standing for
     * a class several bundles run) keep pointing at whichever bundle owned the row; the next
     * discovery of each bundle writes its own component and handler rows next to it.
     *
     * <p>The DDL takes {@code ACCESS EXCLUSIVE} on the three catalog tables for as long as the
     * rewrite of a few hundred rows lasts. Under a short {@code lock_timeout} a busy catalog makes
     * it retry rather than queue every catalog query behind it. Servers of an older version that
     * still run against the same database fail their catalog writes once this has run (routing is
     * in memory and unaffected); upgrade all servers together.
     */
    private void migrateComponentIdentity(Connection connection) throws SQLException {
        if (hasOldComponentShape(connection)) {
            long started = System.nanoTime();
            for (int attempt = 1; ; attempt++) {
                try {
                    rekeyComponents(connection);
                    break;
                } catch (SQLException e) {
                    if (!LOCK_NOT_AVAILABLE.equals(e.getSQLState()) || attempt >= ATTACH_ATTEMPTS) throw e;
                    log.warn("event=catalog_component_rekey_lock_busy attempt={} lockTimeout={}",
                            attempt, ATTACH_LOCK_TIMEOUT);
                    sleep(Math.min(250L * attempt, 2_000L));
                }
            }
            log.info("event=catalog_component_rekeyed durationMs={}", (System.nanoTime() - started) / 1_000_000);
        }
        execute(connection, "CREATE UNIQUE INDEX IF NOT EXISTS core__component_bundle_name_uidx " +
                "ON core__component (bundle_id, component_name)");
        execute(connection, "CREATE INDEX IF NOT EXISTS core__handler_component_id_idx ON core__handler (component_id)");
        execute(connection, "CREATE INDEX IF NOT EXISTS core__consumer_component_id_idx ON core__consumer (component_id)");
    }

    private static void rekeyComponents(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            execute(connection, "SET LOCAL lock_timeout = '" + ATTACH_LOCK_TIMEOUT + "'");
            execute(connection, "LOCK TABLE core__component, core__handler, core__consumer IN ACCESS EXCLUSIVE MODE");
            execute(connection, "ALTER TABLE core__component ADD COLUMN id varchar(512)");
            execute(connection, "UPDATE core__component SET id = coalesce(bundle_id, '') || ':' || component_name");
            for (String table : new String[]{"core__handler", "core__consumer"}) {
                execute(connection, "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS component_id varchar(512)");
                execute(connection, "UPDATE " + table + " t SET component_id = c.id FROM core__component c " +
                        "WHERE c.component_name = t.component_component_name");
                // Drops the foreign key on the old column with it.
                execute(connection, "ALTER TABLE " + table + " DROP COLUMN IF EXISTS component_component_name");
            }
            String oldKey;
            try (var stmt = connection.createStatement();
                 var rs = stmt.executeQuery("SELECT conname FROM pg_constraint " +
                         "WHERE conrelid = 'core__component'::regclass AND contype = 'p'")) {
                oldKey = rs.next() ? rs.getString(1) : null;
            }
            if (oldKey != null) {
                execute(connection, "ALTER TABLE core__component DROP CONSTRAINT \"" + oldKey + "\"");
            }
            execute(connection, "ALTER TABLE core__component ALTER COLUMN id SET NOT NULL");
            execute(connection, "ALTER TABLE core__component ADD CONSTRAINT core__component_pkey PRIMARY KEY (id)");
            execute(connection, "ALTER TABLE core__handler ADD CONSTRAINT core__handler_component_id_fkey " +
                    "FOREIGN KEY (component_id) REFERENCES core__component (id)");
            execute(connection, "ALTER TABLE core__consumer ADD CONSTRAINT core__consumer_component_id_fkey " +
                    "FOREIGN KEY (component_id) REFERENCES core__component (id)");
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /**
     * Adds {@code PRIMARY KEY (event_sequence_number)} to {@code es__events} if it is missing,
     * without holding {@code ACCESS EXCLUSIVE} on the event store for longer than an instant.
     * Runs on the autocommit lock connection: {@code CREATE INDEX CONCURRENTLY} cannot run inside
     * a transaction block.
     */
    private void applyEventStoreConstraints(Connection connection) throws SQLException {
        if (!enforcePrimaryKey) {
            log.warn("event=es_events_primary_key_skipped reason=\"evento.es.schema.enforce-primary-key=false\"");
            return;
        }
        if (queryLong(connection, "SELECT count(*) FROM pg_constraint " +
                "WHERE conrelid = 'es__events'::regclass AND contype = 'p'") > 0) {
            return;
        }
        long duplicated = queryLong(connection, "SELECT count(*) FROM (SELECT event_sequence_number " +
                "FROM es__events GROUP BY event_sequence_number HAVING count(*) > 1) d");
        if (duplicated > 0) {
            throw duplicates(duplicated, null);
        }

        long started = System.nanoTime();
        dropInvalidSequenceIndex(connection);
        try {
            execute(connection, "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS " + ES_EVENTS_SEQ_INDEX +
                    " ON es__events (event_sequence_number)");
        } catch (SQLException e) {
            // A failed concurrent build leaves an INVALID index behind; never leave it for the
            // old servers to maintain on every append.
            dropInvalidSequenceIndex(connection);
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                // An old server appended a duplicate after the check above.
                throw duplicates(-1, e);
            }
            throw e;
        }
        log.info("event=es_events_sequence_index_built index={} durationMs={}",
                ES_EVENTS_SEQ_INDEX, (System.nanoTime() - started) / 1_000_000);

        attachPrimaryKey(connection);
        log.info("event=es_events_primary_key_added");
    }

    private static void attachPrimaryKey(Connection connection) throws SQLException {
        execute(connection, "SET lock_timeout = '" + ATTACH_LOCK_TIMEOUT + "'");
        try {
            for (int attempt = 1; ; attempt++) {
                try {
                    execute(connection, "ALTER TABLE es__events ADD CONSTRAINT es__events_pkey " +
                            "PRIMARY KEY USING INDEX " + ES_EVENTS_SEQ_INDEX);
                    return;
                } catch (SQLException e) {
                    if (!LOCK_NOT_AVAILABLE.equals(e.getSQLState()) || attempt >= ATTACH_ATTEMPTS) throw e;
                    log.warn("event=es_events_primary_key_lock_busy attempt={} lockTimeout={} " +
                            "reason=\"a long-running query holds es__events; retrying\"", attempt, ATTACH_LOCK_TIMEOUT);
                    sleep(Math.min(250L * attempt, 2_000L));
                }
            }
        } finally {
            execute(connection, "RESET lock_timeout");
        }
    }

    private static void dropInvalidSequenceIndex(Connection connection) throws SQLException {
        long invalid = queryLong(connection, "SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid " +
                "WHERE c.relname = '" + ES_EVENTS_SEQ_INDEX + "' AND NOT i.indisvalid");
        if (invalid > 0) {
            log.warn("event=es_events_sequence_index_invalid action=drop index={}", ES_EVENTS_SEQ_INDEX);
            execute(connection, "DROP INDEX CONCURRENTLY IF EXISTS " + ES_EVENTS_SEQ_INDEX);
        }
    }

    private static SQLException duplicates(long count, SQLException cause) {
        String what = count >= 0 ? count + " sequence number(s) are duplicated" : "sequence numbers are duplicated";
        var e = new SQLException("evento: cannot add PRIMARY KEY (event_sequence_number) to es__events: " + what +
                ". Two appenders wrote the same number (APES Snowflake node-id collision or a lost CPES es-lock); " +
                "resolve the duplicates (SELECT event_sequence_number, count(*) FROM es__events GROUP BY 1 " +
                "HAVING count(*) > 1), or boot with evento.es.schema.enforce-primary-key=false", UNIQUE_VIOLATION, cause);
        return e;
    }

    /**
     * Takes {@link #SCHEMA_LOCK_KEY} by polling, so a waiting server never sits in a statement
     * (with an open snapshot) that the holder's {@code CREATE INDEX CONCURRENTLY} would wait for.
     */
    private static void awaitSchemaLock(Connection connection) throws SQLException {
        long backoff = 20;
        while (true) {
            try (var stmt = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                stmt.setLong(1, SCHEMA_LOCK_KEY);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getBoolean(1)) return;
                }
            }
            sleep(backoff);
            backoff = Math.min(backoff * 2, 500);
        }
    }

    private static void advisory(Connection connection, String sql) throws SQLException {
        try (var stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, SCHEMA_LOCK_KEY);
            stmt.execute();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (var stmt = connection.createStatement(); var rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void sleep(long millis) throws SQLException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted during schema initialisation", e);
        }
    }

    private static boolean isPostgres(Connection connection) throws SQLException {
        return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
    }
}
