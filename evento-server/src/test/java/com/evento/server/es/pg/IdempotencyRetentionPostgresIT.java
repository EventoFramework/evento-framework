package com.evento.server.es.pg;

import com.evento.common.utils.PgDistributedLock;
import com.evento.server.es.IdempotencyRetentionAccess;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F11: with a retention, keys older than it leave their events (which stay) and finished or
 * abandoned claims are deleted; a key that went can be used again; without a retention nothing
 * changes.
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class IdempotencyRetentionPostgresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgresItSupport.newContainer();

    private static void event(JdbcTemplate jdbc, long seq, String key, String age) {
        jdbc.update("insert into es__events (event_sequence_number, event_name, event_message, idempotency_key, "
                + "idempotency_command, created_at) values (?, 'E', '{}', ?, ?, "
                + "(now() at time zone 'utc') - ?::interval)", seq, key, key == null ? null : "Pay", age);
    }

    private static void claim(JdbcTemplate jdbc, String key, String completedAgo, String leaseFromNow) {
        jdbc.update("insert into es__idempotency_claims (idempotency_key, command_type, owner, lease_until, completed_at) "
                        + "values (?, 'Pay', 'server-1', now() + ?::interval, "
                        + "case when ?::text is null then null else now() - ?::interval end)",
                key, leaseFromNow, completedAgo, completedAgo);
    }

    @Test
    void keysAndClaimsOlderThanTheRetentionGoAndTheEventsStay() throws Exception {
        PostgresItSupport.createDatabase(POSTGRES, "idem_retention");
        try (var ds = PostgresItSupport.dataSource(POSTGRES, PostgresItSupport.jdbcUrl(POSTGRES, "idem_retention"), 3)) {
            PostgresItSupport.initializer(ds, true).initializeDatabase();
            var jdbc = new JdbcTemplate(ds);
            // 2500 old keys (more than one batch), one recent.
            for (int i = 1; i <= 2_500; i++) event(jdbc, i, "old-" + i, "40 days");
            event(jdbc, 3_000, "recent", "1 day");
            event(jdbc, 3_001, null, "40 days");
            claim(jdbc, "done-long-ago", "40 days", "-40 days");
            claim(jdbc, "abandoned", null, "-40 days");
            claim(jdbc, "done-yesterday", "1 day", "-1 day");
            claim(jdbc, "running", null, "30 seconds");

            var registry = new SimpleMeterRegistry();
            IdempotencyRetentionAccess.sweep(jdbc, new PgDistributedLock(ds), registry, Duration.ofDays(30));

            assertThat(jdbc.queryForObject("select count(*) from es__events", Integer.class))
                    .as("events stay").isEqualTo(2_502);
            assertThat(jdbc.queryForList("select idempotency_key from es__events where idempotency_key is not null",
                    String.class)).containsExactly("recent");
            assertThat(jdbc.queryForObject("select count(*) from es__events where idempotency_command is not null",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForList("select idempotency_key from es__idempotency_claims order by 1", String.class))
                    .containsExactly("done-yesterday", "running");
            assertThat(registry.get("evento.es.idempotency.retention.cleared").tag("what", "event_keys")
                    .counter().count()).isEqualTo(2_500);

            // A key that went can key a new command.
            event(jdbc, 3_002, "old-7", "0 days");
        }
    }

    @Test
    void withoutARetentionNothingIsTouched() throws Exception {
        PostgresItSupport.createDatabase(POSTGRES, "idem_no_retention");
        try (var ds = PostgresItSupport.dataSource(POSTGRES, PostgresItSupport.jdbcUrl(POSTGRES, "idem_no_retention"), 3)) {
            PostgresItSupport.initializer(ds, true).initializeDatabase();
            var jdbc = new JdbcTemplate(ds);
            event(jdbc, 1, "old", "400 days");
            claim(jdbc, "done", "400 days", "-400 days");

            IdempotencyRetentionAccess.scheduled(jdbc, new PgDistributedLock(ds), new SimpleMeterRegistry(), null);

            assertThat(jdbc.queryForObject("select count(*) from es__events where idempotency_key = 'old'", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from es__idempotency_claims", Integer.class)).isEqualTo(1);
        }
    }
}
