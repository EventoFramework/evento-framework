package com.evento.consumer.state.store.jdbc;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/**
 * Thin helper that runs the v2 consumer-state Flyway migrations from this
 * module's classpath. Apps that already use Flyway can skip this and add
 * {@link SqlDialect#migrationLocation()} to their own configuration instead.
 *
 * <p>The migration history is tracked in a dedicated table
 * ({@code evento_v2_schema_history}) so it never collides with an app's own
 * Flyway setup.
 */
public final class FlywayMigrator {

    private FlywayMigrator() {}

    /** Migrate a fresh or partially-migrated database to the latest v2 schema. */
    public static void migrate(DataSource dataSource, SqlDialect dialect) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(dialect.migrationLocation())
                .table("evento_v2_schema_history")
                .baselineOnMigrate(true)
                // Baseline at version 0, NOT Flyway's default of 1. When the
                // target schema already holds the app's own tables, Flyway
                // baselines the (non-empty) schema before applying our
                // migrations and treats every migration with version <=
                // baselineVersion as already-applied. With the default
                // baseline of 1, our only migration (V1__init_v2_consumer_state)
                // would be silently skipped — "no migration necessary" while the
                // evento_v2_* tables never get created. Baselining at 0 keeps V1
                // (1 > 0) in scope so the schema is always created.
                .baselineVersion("0")
                .load()
                .migrate();
    }
}
