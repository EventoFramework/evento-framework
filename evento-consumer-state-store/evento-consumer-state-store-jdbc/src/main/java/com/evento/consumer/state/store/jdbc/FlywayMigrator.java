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
                .load()
                .migrate();
    }
}
