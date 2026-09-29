package com.evento.server.es.pg;

import com.evento.server.es.EventStoreMode;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.TimeUnit;

/**
 * Every {@link IdempotentCommandPostgresIT} scenario on the APES event store, whose keyed append is
 * a plain {@code INSERT ... ON CONFLICT DO NOTHING} with a Snowflake sequence number instead of the
 * CPES single statement (G5 review, LOW-6).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 180, unit = TimeUnit.SECONDS)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class IdempotentCommandApesPostgresIT extends IdempotentCommandPostgresIT {

    @Override
    EventStoreMode mode() {
        return EventStoreMode.APES;
    }
}
