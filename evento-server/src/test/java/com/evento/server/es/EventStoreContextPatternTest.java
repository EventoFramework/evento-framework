package com.evento.server.es;

import com.evento.common.utils.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the context-filter to LIKE translation (G15). The real-Postgres behaviour of the
 * resulting patterns is covered by {@code EventStorePostgresIT}.
 */
class EventStoreContextPatternTest {

    @Test
    void underscoreInAContextNameIsLiteral() {
        assertThat(EventStore.contextLikePattern("SUD_AMERICA")).isEqualTo("SUD!_AMERICA");
        assertThat(EventStore.contextLikePattern("AUSTRALIA_NZ")).isEqualTo("AUSTRALIA!_NZ");
    }

    @Test
    void starIsTheOnlyWildcard() {
        assertThat(EventStore.contextLikePattern("*")).isEqualTo("%");
        assertThat(EventStore.contextLikePattern("SUD_*")).isEqualTo("SUD!_%");
    }

    @Test
    void percentAndTheEscapeCharacterAreEscaped() {
        assertThat(EventStore.contextLikePattern("50%OFF")).isEqualTo("50!%OFF");
        assertThat(EventStore.contextLikePattern("HEY!")).isEqualTo("HEY!!");
        // The queries escape with '!' (independent of standard_conforming_strings), so a
        // backslash is an ordinary character.
        assertThat(EventStore.contextLikePattern("a\\b")).isEqualTo("a\\b");
    }

    @Test
    @SuppressWarnings("deprecation")
    void aContextNamedAllIsLiteralAndOnlyTheStarIsTheWildcard() {
        assertThat(EventStore.contextLikePattern("ALL")).isEqualTo("ALL");
        assertThat(Context.WILDCARD).isEqualTo("*");
        // The trap: the deprecated constant named ALL is the wildcard, not "ALL".
        assertThat(Context.ALL).isEqualTo(Context.WILDCARD);
        assertThat(EventStore.contextLikePattern(Context.WILDCARD)).isEqualTo("%");
    }

    @Test
    void plainNamesAreUnchanged() {
        assertThat(EventStore.contextLikePattern("EUROPA")).isEqualTo("EUROPA");
        assertThat(EventStore.contextLikePattern("default")).isEqualTo("default");
    }
}
