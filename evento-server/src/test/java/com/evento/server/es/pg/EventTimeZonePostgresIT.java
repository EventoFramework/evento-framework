package com.evento.server.es.pg;

import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.serialization.ObjectMapperUtils;
import com.evento.server.es.EventStore;
import com.evento.server.es.EventStoreMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * es__events keeps UTC wall time in plain {@code timestamp} columns. A server whose JVM runs in a
 * zone with daylight saving (here Europe/Rome, which the PostgreSQL driver also gives the session)
 * must still read the instant that was stored: once it read them through the JVM's zone, so event
 * times came out one or two hours off, and a time inside the spring-forward gap one more hour off.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class EventTimeZonePostgresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgresItSupport.newContainer();

    public static class Noted extends DomainEvent implements Serializable {
        public String note;

        public Noted() {
        }

        Noted(String note) {
            this.note = note;
        }
    }

    private static void insert(JdbcTemplate jdbc, long seq, String aggregateId, String createdAtUtc) throws Exception {
        String json = ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(new DomainEventMessage(new Noted("n")));
        jdbc.update("insert into es__events (event_sequence_number, aggregate_id, event_message, event_name, context, created_at) "
                + "values (?, ?, ?, 'Noted', 'EU', cast(? as timestamp))", seq, aggregateId, json, createdAtUtc);
    }

    @Test
    void eventTimesDoNotDependOnTheJvmZoneNorOnDaylightSaving() throws Exception {
        var previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"));
        try {
            PostgresItSupport.createDatabase(POSTGRES, "tz_primary");
            PostgresItSupport.createDatabase(POSTGRES, "tz_replica");
            try (var primary = PostgresItSupport.dataSource(POSTGRES, PostgresItSupport.jdbcUrl(POSTGRES, "tz_primary"), 4);
                 var replica = PostgresItSupport.dataSource(POSTGRES, PostgresItSupport.jdbcUrl(POSTGRES, "tz_replica"), 4)) {
                PostgresItSupport.initializer(primary, true).initializeDatabase();
                PostgresItSupport.initializer(replica, true).initializeDatabase();
                var jdbc = new JdbcTemplate(replica);
                // 02:30 on 2026-03-29 does not exist in Rome (clocks jump from 02:00 to 03:00).
                insert(jdbc, 1, "a-1", "2026-03-29 02:30:00");
                insert(jdbc, 2, "a-1", "2026-07-01 10:00:00");
                // 00:30 UTC on 2026-10-25 is 02:30 in Rome, an hour that happens twice that night.
                insert(jdbc, 3, "a-2", "2026-10-25 00:30:00");

                var store = new EventStore(null, null, new JdbcTemplate(primary), primary, 16, 16, 1000, 1000, 69,
                        EventStoreMode.CPES);
                try {
                    store.configureReadDataSource(PostgresItSupport.jdbcUrl(POSTGRES, "tz_replica"),
                            POSTGRES.getUsername(), POSTGRES.getPassword(), 2);
                    var fetched = store.fetchEvents("EU", 0L, 10);

                    assertThat(fetched).extracting(e -> e.createdAtInstant()).containsExactly(
                            Instant.parse("2026-03-29T02:30:00Z"),
                            Instant.parse("2026-07-01T10:00:00Z"),
                            Instant.parse("2026-10-25T00:30:00Z"));
                    assertThat(fetched.getFirst().toPublishedEvent().getCreatedAt())
                            .isEqualTo(Instant.parse("2026-03-29T02:30:00Z").toEpochMilli());
                } finally {
                    store.close();
                }
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void aDeletionIsStampedInUtcLikeTheEvents() throws Exception {
        var previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"));
        try {
            PostgresItSupport.createDatabase(POSTGRES, "tz_delete");
            try (var ds = PostgresItSupport.dataSource(POSTGRES, PostgresItSupport.jdbcUrl(POSTGRES, "tz_delete"), 4)) {
                PostgresItSupport.initializer(ds, true).initializeDatabase();
                var jdbc = new JdbcTemplate(ds);
                insert(jdbc, 1, "a-9", "2026-07-01 10:00:00");
                var store = new EventStore(null, null, jdbc, ds, 16, 16, 1000, 1000, 69, EventStoreMode.CPES);
                try {
                    var before = Instant.now();
                    store.deleteAggregate("a-9");
                    store.configureReadDataSource(PostgresItSupport.jdbcUrl(POSTGRES, "tz_delete"),
                            POSTGRES.getUsername(), POSTGRES.getPassword(), 2);
                    var deletedAt = store.fetchEvents("EU", 0L, 10).getFirst().deletedAtInstant();

                    assertThat(Duration.between(before, deletedAt).abs()).isLessThan(Duration.ofMinutes(1));
                } finally {
                    store.close();
                }
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
