package com.evento.lab.cluster.support;

import com.evento.common.utils.Context;
import com.evento.lab.cluster.api.Contexts;
import com.evento.lab.cluster.api.Regions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** EVENTO_CONTEXTS: regions, the literal ALL and the wildcard * are three different things. */
class ContextSelectionTest {

    @Test
    void regionListStartsOneEnginePerRegionAndNoGlobalLane() {
        var s = ContextSelection.parse("EUROPA,AFRICA");
        assertThat(s.contexts()).containsExactly("EUROPA", "AFRICA");
        assertThat(s.regional()).containsExactly("EUROPA", "AFRICA");
        assertThat(s.global()).isEmpty();
        assertThat(s.isWildcard()).isFalse();
        assertThat(s.regions()).containsExactly("EUROPA", "AFRICA");
    }

    @Test
    void literalAllIsTheGlobalContextNotTheWildcard() {
        var s = ContextSelection.parse("ALL");
        assertThat(s.contexts()).containsExactly(Contexts.GLOBAL);
        assertThat(s.contexts()).doesNotContain(Context.ALL);
        assertThat(s.isWildcard()).isFalse();
        assertThat(s.global()).containsExactly("ALL");
        assertThat(s.regional()).isEmpty();
        assertThat(s.regions()).isEmpty();
        // lower case still means the literal context
        assertThat(ContextSelection.parse(" all ").contexts()).containsExactly("ALL");
    }

    @Test
    void starIsTheWildcardAndDoesNotEnableTheGlobalLane() {
        var s = ContextSelection.parse("*");
        assertThat(s.isWildcard()).isTrue();
        assertThat(s.contexts()).containsExactly(Context.ALL);
        // a wildcard order projector sees every region...
        assertThat(s.regional()).containsExactly("*");
        assertThat(s.regions()).isEqualTo(Regions.ALL);
        // ...but the global lane runs only where ALL is named: one lane cluster-wide
        assertThat(s.global()).isEmpty();
        assertThat(ContextSelection.wildcard()).isEqualTo(s);
    }

    @Test
    void regionsAndAllTogetherSplitPerComponent() {
        var s = ContextSelection.parse("cina, ALL, sud-america, CINA");
        assertThat(s.contexts()).containsExactly("CINA", "ALL", "SUD_AMERICA");
        assertThat(s.regional()).containsExactly("CINA", "SUD_AMERICA");
        assertThat(s.global()).containsExactly("ALL");
        assertThat(s.toString()).isEqualTo("CINA,ALL,SUD_AMERICA");
    }

    @Test
    void rejectsWhatWouldBecomeASilentLane() {
        assertThatThrownBy(() -> ContextSelection.parse("")).hasMessageContaining("empty");
        assertThatThrownBy(() -> ContextSelection.parse(" , ")).hasMessageContaining("no context");
        assertThatThrownBy(() -> ContextSelection.parse("*,EUROPA")).hasMessageContaining("cannot be combined");
        assertThatThrownBy(() -> ContextSelection.parse("EUROPA,ALL,*")).hasMessageContaining("cannot be combined");
        assertThatThrownBy(() -> ContextSelection.parse("SUDXAMERICA")).hasMessageContaining("unknown context");
        assertThatThrownBy(() -> ContextSelection.parse("%")).hasMessageContaining("unknown context");
    }
}
