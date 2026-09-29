package com.evento.server.es;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the context-filter to LIKE translation (G15). The real-Postgres behaviour of the
 * resulting patterns is covered by {@code EventStorePostgresIT}.
 */
class EventStoreContextPatternTest {

    @Test
    void underscoreInAContextNameIsLiteral() {
        assertThat(EventStore.contextLikePattern("SUD_AMERICA")).isEqualTo("SUD\\_AMERICA");
        assertThat(EventStore.contextLikePattern("AUSTRALIA_NZ")).isEqualTo("AUSTRALIA\\_NZ");
    }

    @Test
    void starIsTheOnlyWildcard() {
        assertThat(EventStore.contextLikePattern("*")).isEqualTo("%");
        assertThat(EventStore.contextLikePattern("SUD_*")).isEqualTo("SUD\\_%");
    }

    @Test
    void percentAndBackslashAreEscaped() {
        assertThat(EventStore.contextLikePattern("50%OFF")).isEqualTo("50\\%OFF");
        assertThat(EventStore.contextLikePattern("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void plainNamesAreUnchanged() {
        assertThat(EventStore.contextLikePattern("EUROPA")).isEqualTo("EUROPA");
        assertThat(EventStore.contextLikePattern("default")).isEqualTo("default");
    }
}
