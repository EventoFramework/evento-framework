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
 * same instant, the idempotent, fail-loud {@code es__events} primary key, and the in-place
 * per-bundle re-keying of an old component catalog (N1).
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

    @Test
    @Timeout(120)
    void addingThePrimaryKeyNeverParksAppendsBehindALongRunningReader() throws Exception {
        // Rolling upgrade: the first new server adds the PK while an old server holds a long
        // transaction on es__events and others keep appending. A plain ALTER ... ADD PRIMARY KEY
        // would queue for ACCESS EXCLUSIVE behind the reader, and every append behind the ALTER,
        // for as long as the reader runs.
        PostgresItSupport.createDatabase(POSTGRES, "schema_rolling");
        String url = PostgresItSupport.jdbcUrl(POSTGRES, "schema_rolling");
        try (var ds = PostgresItSupport.dataSource(POSTGRES, url, 3);
             var oldServer = PostgresItSupport.dataSource(POSTGRES, url, 3)) {
            PostgresItSupport.initializer(ds, false).initializeDatabase();
            var jdbc = new JdbcTemplate(ds);
            for (int i = 1; i <= 1_000; i++) {
                jdbc.update("insert into es__events (event_sequence_number, event_name, event_message) values (?, 'E', '{}')", i);
            }
            assertThat(primaryKeyColumns(jdbc)).isEmpty();

            long readerHoldMs = 8_000;
            try (var reader = oldServer.getConnection()) {
                reader.setAutoCommit(false);
                try (var st = reader.createStatement()) {
                    st.executeQuery("select count(*) from es__events").close();
                }
                var init = Thread.ofPlatform().start(() -> PostgresItSupport.initializer(ds, true).initializeDatabase());
                Thread.sleep(1_000);

                // Appends go through while the key waits for its instant of ACCESS EXCLUSIVE:
                // at worst one lock_timeout behind the swap attempt, never the reader's duration.
                var appender = new JdbcTemplate(oldServer);
                long worstMs = 0;
                long until = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(readerHoldMs - 2_000);
                long seq = 10_000;
                while (System.nanoTime() < until) {
                    long t0 = System.nanoTime();
                    appender.update("insert into es__events (event_sequence_number, event_name, event_message) values (?, 'E', '{}')", seq++);
                    worstMs = Math.max(worstMs, (System.nanoTime() - t0) / 1_000_000);
                    Thread.sleep(50);
                }
                assertThat(worstMs).as("worst append latency while the PK waits").isLessThan(readerHoldMs - 3_000);
                assertThat(primaryKeyColumns(jdbc)).as("the swap waits for the reader").isEmpty();

                reader.commit();
                init.join(60_000);
                assertThat(init.isAlive()).isFalse();
            }
            assertThat(primaryKeyColumns(jdbc)).containsExactly("event_sequence_number");
            assertThat(jdbc.queryForObject(
                    "select count(*) from pg_index i join pg_class c on c.oid = i.indexrelid " +
                    "where i.indrelid = 'es__events'::regclass and not i.indisvalid", Integer.class))
                    .as("no invalid index left behind").isZero();
        }
    }

    /**
     * N1: a catalog created before components were keyed per bundle is re-keyed in place on
     * the first boot of the new version, keeping its rows and their links, and later boots
     * leave it alone.
     */
    @Test
    void aCatalogKeyedByComponentNameIsRekeyedPerBundleInPlace() throws Exception {
        PostgresItSupport.createDatabase(POSTGRES, "schema_rekey");
        try (var ds = PostgresItSupport.dataSource(POSTGRES, PostgresItSupport.jdbcUrl(POSTGRES, "schema_rekey"), 2)) {
            var jdbc = new JdbcTemplate(ds);
            // The catalog tables as the previous schema.sql created them.
            jdbc.execute("""
                    create table core__bundle (id varchar(255) not null primary key, instance_id varchar(255) null,
                        contains_handlers boolean not null, description text null, detail text null,
                        line_prefix varchar(255) null, repository_url text null, updated_at timestamp null,
                        version bigint not null);
                    create table core__component (component_name varchar(255) not null primary key,
                        component_type varchar(255) null, description text null, detail text null, line int null,
                        path text null, updated_at timestamp null, bundle_id varchar(255) null,
                        foreign key (bundle_id) references core__bundle (id));
                    create table core__consumer (identifier varchar(512) not null primary key,
                        component_component_name varchar(255) null, instance_id varchar(255) null,
                        consumer_id varchar(255) null,
                        foreign key (component_component_name) references core__component (component_name));
                    create table core__handler (uuid varchar(255) not null primary key,
                        association_property varchar(255) null, handler_type varchar(255) null, line int null,
                        executor varchar(255) null, return_is_multiple boolean not null,
                        component_component_name varchar(255) null, handled_payload_name varchar(255) null,
                        return_type_name varchar(255) null,
                        foreign key (component_component_name) references core__component (component_name));
                    insert into core__bundle (id, contains_handlers, version) values ('query-cina', true, 1);
                    insert into core__component (component_name, component_type, bundle_id)
                        values ('OrderProjector', 'Projector', 'query-cina');
                    insert into core__handler (uuid, handler_type, return_is_multiple, component_component_name,
                        handled_payload_name) values ('h1', 'EventHandler', false, 'OrderProjector', 'OrderCreated');
                    insert into core__consumer (identifier, component_component_name, instance_id, consumer_id)
                        values ('i1_query-cina_OrderProjector_1_CINA', 'OrderProjector', 'i1',
                                'query-cina_OrderProjector_1_CINA');
                    """);

            PostgresItSupport.initializer(ds, true).initializeDatabase();

            assertThat(jdbc.queryForList("select id from core__component", String.class))
                    .containsExactly("query-cina:OrderProjector");
            assertThat(jdbc.queryForObject("select component_id from core__handler where uuid = 'h1'", String.class))
                    .isEqualTo("query-cina:OrderProjector");
            assertThat(jdbc.queryForObject("select component_id from core__consumer", String.class))
                    .isEqualTo("query-cina:OrderProjector");
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_name in ('core__handler', 'core__consumer') and column_name = 'component_component_name'
                    """, Integer.class)).as("old link columns dropped").isZero();
            assertThat(jdbc.queryForList("""
                    select a.attname from pg_constraint c
                    join pg_attribute a on a.attrelid = c.conrelid and a.attnum = any (c.conkey)
                    where c.conrelid = 'core__component'::regclass and c.contype = 'p'
                    """, String.class)).containsExactly("id");

            // Idempotent, and the same class now fits in a second bundle.
            PostgresItSupport.initializer(ds, true).initializeDatabase();
            jdbc.update("insert into core__bundle (id, contains_handlers, version) values ('query-europa', true, 1)");
            jdbc.update("""
                    insert into core__component (id, component_name, component_type, bundle_id)
                    values ('query-europa:OrderProjector', 'OrderProjector', 'Projector', 'query-europa')
                    """);
            assertThat(jdbc.queryForObject("select count(*) from core__component where component_name = 'OrderProjector'",
                    Integer.class)).isEqualTo(2);
            assertThatThrownBy(() -> jdbc.update(
                    "insert into core__consumer (identifier, component_id) values ('x', 'nobody:Nothing')"))
                    .as("consumer rows still reference a real component")
                    .hasMessageContaining("core__consumer_component_id_fkey");
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
