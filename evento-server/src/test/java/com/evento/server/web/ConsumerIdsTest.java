package com.evento.server.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G16: the consumers page split consumer ids on {@code _} and took fixed indexes,
 * so a context with an underscore ({@code SUD_AMERICA}) showed as {@code SUD}, and
 * a bundle id with an underscore shifted every field.
 */
class ConsumerIdsTest {

    @Test
    void contextWithUnderscoresIsKeptWhole() {
        var parts = ConsumerIds.parse("query-sud_america_RegionOrderProjector_3_SUD_AMERICA",
                "query-sud_america", "RegionOrderProjector").orElseThrow();

        assertThat(parts.version()).isEqualTo("3");
        assertThat(parts.context()).isEqualTo("SUD_AMERICA");
    }

    @Test
    void plainIdsStillParse() {
        var parts = ConsumerIds.parse("cluster-query_OrderProjector_1_default",
                "cluster-query", "OrderProjector").orElseThrow();

        assertThat(parts.version()).isEqualTo("1");
        assertThat(parts.context()).isEqualTo("default");
    }

    @Test
    void wildcardContextParses() {
        var parts = ConsumerIds.parse("query_global_OrderProjector_12_*",
                "query_global", "OrderProjector").orElseThrow();

        assertThat(parts.version()).isEqualTo("12");
        assertThat(parts.context()).isEqualTo("*");
    }

    @Test
    void componentRowOwnedByAnotherBundleFallsBackToTheComponentName() {
        // core__component is keyed by name only, so the row may name another bundle.
        var parts = ConsumerIds.parse("query-australia_nz_RegionOrderProjector_2_AUSTRALIA_NZ",
                "query-cina", "RegionOrderProjector").orElseThrow();

        assertThat(parts.version()).isEqualTo("2");
        assertThat(parts.context()).isEqualTo("AUSTRALIA_NZ");
    }

    @Test
    void malformedIdsYieldEmptyInsteadOfThrowing() {
        assertThat(ConsumerIds.parse("garbage", "b", "C")).isEmpty();
        assertThat(ConsumerIds.parse("b_C_notanumber_X", "b", "C")).isEmpty();
        assertThat(ConsumerIds.parse(null, "b", "C")).isEmpty();
    }
}
