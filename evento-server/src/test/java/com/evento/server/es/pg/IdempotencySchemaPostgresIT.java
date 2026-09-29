package com.evento.server.es.pg;

import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.server.es.EventStore;
import com.evento.server.es.EventStoreMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the event store and the schema initializer decide that idempotency keys can be enforced
 * (G5 review, MEDIUM-4): the index they look for must guard the {@code es__events} the server
 * actually writes (the one {@code search_path} resolves), not any index of that name in the
 * database, and an index that disappears after it was found turns keys off instead of failing
 * every keyed append with {@code 42P10}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class IdempotencySchemaPostgresIT {

    private PostgreSQLContainer postgres;

    @BeforeAll
    void start() {
        postgres = PostgresItSupport.newContainer();
        postgres.start();
    }

    @AfterAll
    void stop() {
        if (postgres != null) postgres.stop();
    }

    public static class Evt extends DomainEvent implements Serializable {
        public String id = "e";
    }

    private static EventStore store(javax.sql.DataSource ds, EventStoreMode mode) throws Exception {
        return new EventStore(null, null, new JdbcTemplate(ds), ds, 16, 16, 1000, 1000, 69, mode);
    }

    @Test
    void anIndexOfTheSameNameInAnotherSchemaNeitherEnablesKeysNorStopsTheInitializerFromBuildingOurs() throws Exception {
        PostgresItSupport.createDatabase(postgres, "idem_schema");
        try (var ds = PostgresItSupport.dataSource(postgres, PostgresItSupport.jdbcUrl(postgres, "idem_schema"), 3)) {
            var jdbc = new JdbcTemplate(ds);
            // The server's schema (public, first on search_path) is fully set up except for the
            // index; a second schema (another tenant, a restored copy) has a complete one.
            for (var schema : new String[]{"public", "other"}) {
                if (!schema.equals("public")) jdbc.execute("CREATE SCHEMA " + schema);
                jdbc.execute("CREATE TABLE " + schema + ".es__events (event_sequence_number bigint NOT NULL, "
                        + "context varchar(100) DEFAULT 'default', aggregate_id varchar(100), event_name varchar(100) NOT NULL, "
                        + "created_at timestamp NOT NULL DEFAULT now(), event_message text NOT NULL, deleted_at timestamp, "
                        + "idempotency_key varchar(255), idempotency_command varchar(255))");
                jdbc.execute("CREATE TABLE " + schema + ".es__idempotency_claims (idempotency_key varchar(255) PRIMARY KEY, "
                        + "command_type varchar(255) NOT NULL, aggregate_id varchar(255), owner varchar(255) NOT NULL, "
                        + "lease_until timestamptz NOT NULL, completed_at timestamptz, created_at timestamptz NOT NULL DEFAULT now())");
            }
            jdbc.execute("CREATE SEQUENCE event_sequence_number_serial");
            jdbc.execute("CREATE UNIQUE INDEX " + EventStore.IDEMPOTENCY_KEY_INDEX
                    + " ON other.es__events (idempotency_key) WHERE idempotency_key IS NOT NULL");

            var store = store(ds, EventStoreMode.CPES);
            assertThat(store.idempotencySupported())
                    .as("other.es__events's index does not guard public.es__events").isFalse();
            // Keyed append on an unguarded table: appended unkeyed, not refused, not "enforced".
            long seq = store.publishEvent(new DomainEventMessage(new Evt()), "a-1", "k-1", "Cmd");
            assertThat(jdbc.queryForObject("select idempotency_key from public.es__events where event_sequence_number = ?",
                    String.class, seq)).isNull();

            // The initializer must build the index on public.es__events, not skip it because an
            // index of that name exists elsewhere.
            PostgresItSupport.initializer(ds, false).initializeDatabase();
            assertThat(jdbc.queryForObject("select count(*) from pg_index i join pg_class c on c.oid = i.indexrelid "
                            + "where i.indrelid = 'public.es__events'::regclass and c.relname = ? and i.indisvalid",
                    Integer.class, EventStore.IDEMPOTENCY_KEY_INDEX)).isEqualTo(1);
            assertThat(store(ds, EventStoreMode.CPES).idempotencySupported()).isTrue();
        }
    }

    @Test
    void anIndexDroppedAfterItWasFoundTurnsKeysOffInsteadOfFailingEveryKeyedAppend() throws Exception {
        PostgresItSupport.createDatabase(postgres, "idem_dropped");
        try (var ds = PostgresItSupport.dataSource(postgres, PostgresItSupport.jdbcUrl(postgres, "idem_dropped"), 3)) {
            var jdbc = new JdbcTemplate(ds);
            PostgresItSupport.initializer(ds, true).initializeDatabase();
            for (var mode : EventStoreMode.values()) {
                if (!jdbcHasIndex(jdbc)) {
                    jdbc.execute("CREATE UNIQUE INDEX " + EventStore.IDEMPOTENCY_KEY_INDEX
                            + " ON es__events (idempotency_key) WHERE idempotency_key IS NOT NULL");
                }
                var store = store(ds, mode);
                assertThat(store.idempotencySupported()).isTrue();
                store.publishEvent(new DomainEventMessage(new Evt()), "a-" + mode, "k-" + mode, "Cmd");

                jdbc.execute("DROP INDEX " + EventStore.IDEMPOTENCY_KEY_INDEX);
                // Before: ON CONFLICT had no arbiter (42P10) and every keyed append failed for as
                // long as the server ran. Now the append goes through unkeyed and keys are off.
                long seq = store.publishEvent(new DomainEventMessage(new Evt()), "b-" + mode, "k2-" + mode, "Cmd");
                assertThat(jdbc.queryForObject("select count(*) from es__events where event_sequence_number = ? "
                        + "and idempotency_key is null", Integer.class, seq)).isEqualTo(1);
                assertThat(store.idempotencySupported()).isFalse();
                assertThat(store.findIdempotencyRecord("k-" + mode)).as("no lookups while off").isEmpty();
            }
        }
    }

    private static boolean jdbcHasIndex(JdbcTemplate jdbc) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass(?) is not null", Boolean.class,
                EventStore.IDEMPOTENCY_KEY_INDEX));
    }
}
