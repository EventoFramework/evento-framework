package com.evento.server.es.pg;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G14 + G6 (PK part) against a real Postgres: {@code schema.sql} run by several servers at the
 * same instant, and the idempotent, fail-loud {@code es__events} primary key.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class SchemaInitializationPostgresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgresItSupport.newContainer();

    @Test
    @Timeout(300)
    void threeServersInitialisingAFreshDatabaseAtOnceAllSucceed() throws Exception {
        // Without serialisation the concurrent CREATE/ALTER/CREATE OR REPLACE VIEW statements
        // intermittently abort a server's boot (pg_type_typname_nsp_index, "tuple concurrently
        // updated"). Several rounds on fresh databases to give a race every chance to show.
        for (int round = 0; round < 6; round++) {
            String db = "schema_race_" + round;
            PostgresItSupport.createDatabase(POSTGRES, db);
            List<HikariDataSource> servers = new ArrayList<>();
            for (int s = 0; s < 3; s++) {
                servers.add(PostgresItSupport.dataSource(POSTGRES, PostgresItSupport.jdbcUrl(POSTGRES, db), 3));
            }
            try {
                var barrier = new CyclicBarrier(servers.size());
                var failures = new ConcurrentLinkedQueue<Throwable>();
                List<Thread> threads = new ArrayList<>();
                for (var ds : servers) {
                    var init = PostgresItSupport.initializer(ds, true);
                    var t = Thread.ofPlatform().start(() -> {
                        try {
                            barrier.await();
                            init.initializeDatabase();
                        } catch (Throwable e) {
                            failures.add(e);
                        }
                    });
                    threads.add(t);
                }
                for (var t : threads) t.join();
                assertThat(failures).as("round %d: every server boots", round).isEmpty();

                var jdbc = new JdbcTemplate(servers.getFirst());
                assertThat(primaryKeyColumns(jdbc)).containsExactly("event_sequence_number");
                assertThat(jdbc.queryForObject(
                        "select count(*) from pg_class where relname = 'event_sequence_number_serial'", Integer.class))
                        .isEqualTo(1);
            } finally {
                servers.forEach(HikariDataSource::close);
            }
        }
    }

    @Test
    void reRunningTheInitializerIsIdempotent() throws Exception {
        PostgresItSupport.createDatabase(POSTGRES, "schema_rerun");
        try (var ds = PostgresItSupport.dataSource(POSTGRES, PostgresItSupport.jdbcUrl(POSTGRES, "schema_rerun"), 2)) {
            PostgresItSupport.initializer(ds, true).initializeDatabase();
            PostgresItSupport.initializer(ds, true).initializeDatabase();
            var jdbc = new JdbcTemplate(ds);
            assertThat(primaryKeyColumns(jdbc)).containsExactly("event_sequence_number");

            jdbc.update("insert into es__events (event_sequence_number, event_name, event_message) values (1, 'E', '{}')");
            assertThatThrownBy(() -> jdbc.update(
                    "insert into es__events (event_sequence_number, event_name, event_message) values (1, 'E', '{}')"))
                    .as("the database itself now refuses a duplicate sequence number")
                    .hasMessageContaining("es__events_pkey");
        }
    }

    @Test
    void existingDuplicatesFailTheBootLoudlyUnlessExplicitlyWaived() throws Exception {
        PostgresItSupport.createDatabase(POSTGRES, "schema_dupes");
        try (var ds = PostgresItSupport.dataSource(POSTGRES, PostgresItSupport.jdbcUrl(POSTGRES, "schema_dupes"), 2)) {
            // A pre-existing store without the PK, already holding a duplicated number.
            PostgresItSupport.initializer(ds, false).initializeDatabase();
            var jdbc = new JdbcTemplate(ds);
            assertThat(primaryKeyColumns(jdbc)).isEmpty();
            jdbc.update("insert into es__events (event_sequence_number, event_name, event_message) values (7, 'E', '{}')");
            jdbc.update("insert into es__events (event_sequence_number, event_name, event_message) values (7, 'E', '{}')");

            assertThatThrownBy(() -> PostgresItSupport.initializer(ds, true).initializeDatabase())
                    .hasStackTraceContaining("1 sequence number(s) are duplicated")
                    .hasStackTraceContaining("evento.es.schema.enforce-primary-key=false");
            assertThat(primaryKeyColumns(jdbc)).isEmpty();

            // The operator's escape hatch: boot without the constraint.
            assertThatCode(() -> PostgresItSupport.initializer(ds, false).initializeDatabase())
                    .doesNotThrowAnyException();

            // Once cleaned up, the next boot adds the key.
            jdbc.update("delete from es__events where ctid = (select min(ctid) from es__events where event_sequence_number = 7)");
            PostgresItSupport.initializer(ds, true).initializeDatabase();
            assertThat(primaryKeyColumns(jdbc)).containsExactly("event_sequence_number");
        }
    }

    private static List<String> primaryKeyColumns(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                select a.attname from pg_constraint c
                join pg_attribute a on a.attrelid = c.conrelid and a.attnum = any (c.conkey)
                where c.conrelid = 'es__events'::regclass and c.contype = 'p'
                """, String.class);
    }
}
