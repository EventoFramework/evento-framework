package com.evento.lab.cluster.api;

import com.evento.common.utils.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextsTest {

    @Test
    void theLiteralGlobalContextIsNotTheFrameworkWildcard() {
        // The trap this class exists for: Context.ALL is "*", not "ALL".
        assertThat(Context.ALL).isEqualTo("*");
        assertThat(Contexts.GLOBAL).isEqualTo("ALL").isNotEqualTo(Context.ALL);
        assertThat(Contexts.WILDCARD).isEqualTo(Context.ALL);
        assertThat(Contexts.isGlobal("ALL")).isTrue();
        assertThat(Contexts.isGlobal("*")).isFalse();
        assertThat(Contexts.isWildcard("*")).isTrue();
        assertThat(Contexts.isWildcard("ALL")).isFalse();
    }

    @Test
    void theGlobalContextNeedsNoLikeEscaping() {
        // LIKE metacharacters in a context turn an exact match into a pattern (G15).
        assertThat(Contexts.GLOBAL).doesNotContain("_", "%", "*");
        assertThat(Regions.isRegion(Contexts.GLOBAL)).isFalse();
    }

    @Test
    void everyRegionHasACell() {
        for (var r : Regions.ALL) {
            assertThat(Regions.cellOf(r)).isIn(Regions.CELLS);
            assertThat(Regions.regionsOf(Regions.cellOf(r))).contains(r);
        }
        assertThat(Regions.regionsOf("apac")).containsExactly("CINA", "INDIA", "AUSTRALIA_NZ");
        assertThat(Regions.normalize("sud-america")).isEqualTo("SUD_AMERICA");
        assertThatThrownBy(() -> Regions.normalize("ATLANTIS")).hasMessageContaining("unknown region");
    }
}
