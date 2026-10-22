package com.evento.server.es;

import com.evento.common.utils.PgDistributedLock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Retention of idempotency keys (F11). A keyed command's key is stored with its event
 * ({@code es__events.idempotency_key}, under a partial unique index) and, for a service command,
 * in a claim ({@value EventStore#IDEMPOTENCY_CLAIMS_TABLE}); without a retention both only grow.
 *
 * <p>With {@code evento.es.idempotency.retention} set (an ISO-8601 duration such as {@code P30D}),
 * every {@code evento.es.idempotency.retention-interval} (default one hour) one server (a
 * distributed try-lock; the others skip that round) clears the keys of events older than the
 * retention, in batches of {@value #BATCH} rows so no statement holds many row locks, and deletes
 * the claims completed, or abandoned (lease expired), before it. The events stay; only their key
 * goes. After that, a command sent again with one of those keys runs again: choose a retention
 * longer than any caller can retry, including consumers that replay from a rewound checkpoint.
 * Unset, the default, keeps keys for ever, as before 3.0.
 *
 * <p>The service bundles' outcome store has its own retention
 * ({@code EventoBundle.Builder#setServiceOutcomeRetention}); keep it at least as long as this one.
 */
@Component
public class IdempotencyRetention {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyRetention.class);

    static final String LOCK = "evento-idempotency-retention";
    static final int BATCH = 1000;

    static final String CLEAR_KEYS_SQL =
            "UPDATE es__events SET idempotency_key = NULL, idempotency_command = NULL "
                    + "WHERE event_sequence_number IN (SELECT event_sequence_number FROM es__events "
                    + "WHERE idempotency_key IS NOT NULL "
                    // created_at holds UTC wall time.
                    + "AND created_at < (now() AT TIME ZONE 'utc') - make_interval(secs => ?) LIMIT " + BATCH + ")";

    static final String DELETE_CLAIMS_SQL =
            "DELETE FROM " + EventStore.IDEMPOTENCY_CLAIMS_TABLE + " WHERE idempotency_key IN ("
                    + "SELECT idempotency_key FROM " + EventStore.IDEMPOTENCY_CLAIMS_TABLE + " "
                    + "WHERE coalesce(completed_at, lease_until) < now() - make_interval(secs => ?) LIMIT " + BATCH + ")";

    private final JdbcTemplate jdbc;
    private final PgDistributedLock lock;
    private final Duration retention;
    private final Counter keysCleared;
    private final Counter claimsDeleted;

    public IdempotencyRetention(JdbcTemplate jdbc, PgDistributedLock lock, MeterRegistry registry,
                                @Value("${evento.es.idempotency.retention:}") Duration retention) {
        this.jdbc = jdbc;
        this.lock = lock;
        this.retention = retention == null || retention.isZero() || retention.isNegative() ? null : retention;
        this.keysCleared = Counter.builder("evento.es.idempotency.retention.cleared")
                .description("Idempotency keys removed from events older than the retention")
                .tag("what", "event_keys").register(registry);
        this.claimsDeleted = Counter.builder("evento.es.idempotency.retention.cleared")
                .description("Idempotency claims deleted, completed or abandoned before the retention")
                .tag("what", "claims").register(registry);
        if (this.retention != null) {
            log.info("event=idempotency_retention_enabled retention={}", this.retention);
        }
    }

    @Scheduled(initialDelayString = "${evento.es.idempotency.retention-initial-delay:PT5M}",
            fixedDelayString = "${evento.es.idempotency.retention-interval:PT1H}")
    public void sweep() {
        if (retention == null) return;
        lock.tryLockedArea(LOCK, this::sweepNow);
    }

    /** One complete sweep, without the lock; package-private for the tests. */
    void sweepNow() {
        long seconds = retention.toSeconds();
        long keys = drain(CLEAR_KEYS_SQL, seconds);
        long claims = drain(DELETE_CLAIMS_SQL, seconds);
        keysCleared.increment(keys);
        claimsDeleted.increment(claims);
        if (keys > 0 || claims > 0) {
            log.info("event=idempotency_retention_swept eventKeysCleared={} claimsDeleted={} retention={}",
                    keys, claims, retention);
        }
    }

    private long drain(String sql, long seconds) {
        long total = 0;
        while (true) {
            int n = jdbc.update(sql, seconds);
            total += n;
            if (n < BATCH) return total;
        }
    }
}
