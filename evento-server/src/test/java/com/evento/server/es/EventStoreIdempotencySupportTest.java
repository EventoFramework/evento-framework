package com.evento.server.es;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idempotency keys need PostgreSQL (G5 review, LOW-1): on another database the store says so
 * once and never probes PostgreSQL's catalog, which would fail every keyed command instead.
 */
class EventStoreIdempotencySupportTest {

    @Test
    void anotherDatabaseIsNeverConsideredToSupportKeys() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:idem-support;DB_CLOSE_DELAY=-1", "sa", "");
        var store = new EventStore(null, null, new JdbcTemplate(ds), ds, 16, 16, 1000, 1000, 69, EventStoreMode.CPES);
        assertThat(store.idempotencySupported()).isFalse();
        assertThat(store.idempotencySupported()).as("cached, no second probe").isFalse();
        assertThat(store.findIdempotencyRecord("k-1")).isEmpty();
    }
}
