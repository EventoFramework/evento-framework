/**
 * JDBC implementation of the v2 consumer state SPI.
 *
 * <p>Supports Postgres and MySQL via {@link SqlDialect}. Migrations live under
 * {@code db/migration/{postgres,mysql}/v2/} on this module's classpath; run them
 * via {@link FlywayMigrator#migrate(javax.sql.DataSource, SqlDialect)} or by
 * adding the same location to an app's own Flyway configuration.
 *
 * <p>{@link JdbcConsumerStateStore} and {@link JdbcDedupeStore} both take a
 * {@link javax.sql.DataSource} — they hold no connections of their own; lifecycle
 * is the caller's responsibility (typically a connection pool like HikariCP).
 */
package com.evento.consumer.state.store.jdbc.v2;
