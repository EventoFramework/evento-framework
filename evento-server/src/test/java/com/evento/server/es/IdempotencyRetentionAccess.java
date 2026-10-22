package com.evento.server.es;

import com.evento.common.utils.PgDistributedLock;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

/** Test access to {@link IdempotencyRetention} from the Postgres IT package. */
public final class IdempotencyRetentionAccess {

    private IdempotencyRetentionAccess() {
    }

    /** One sweep, as the scheduled one does it on the server that gets the lock. */
    public static void sweep(JdbcTemplate jdbc, PgDistributedLock lock, MeterRegistry registry, Duration retention) {
        new IdempotencyRetention(jdbc, lock, registry, retention).sweepNow();
    }

    /** The scheduled entry point, retention included. */
    public static void scheduled(JdbcTemplate jdbc, PgDistributedLock lock, MeterRegistry registry, Duration retention) {
        new IdempotencyRetention(jdbc, lock, registry, retention).sweep();
    }
}
