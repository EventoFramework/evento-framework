package com.evento.lab.cluster.chaos.scenario;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure helpers of the scenarios (no stack; not gated). */
class ScenarioMathTest {

    @Test
    void longestStallIsMeasuredInsideThePartitionWindowOnly() {
        var t0 = Instant.parse("2026-09-29T10:00:00Z");
        List<Map<String, Object>> samples = List.of(
                Map.of("t", t0.toString(), "lane", 10L),
                Map.of("t", t0.plusSeconds(5).toString(), "lane", 10L),
                Map.of("t", t0.plusSeconds(10).toString(), "lane", 10L),
                Map.of("t", t0.plusSeconds(15).toString(), "lane", 12L),
                Map.of("t", t0.plusSeconds(20).toString(), "lane", 12L),
                Map.of("t", t0.plusSeconds(60).toString(), "lane", 12L));
        var stall = S07RegionPartitionTest.longestStallSeconds(samples, t0.plusSeconds(5), t0.plusSeconds(30));
        assertThat(stall).containsEntry("lane", 5L);
    }

    @Test
    void s16CountsOnlySequencesBothReplicasFannedOut() {
        var twice = S16GlobalFanOutTest.processedTwice(java.util.Map.of(
                "order-query-global-eu", java.util.List.of(10L, 11L, 11L, 12L),
                "order-query-global-apac", java.util.List.of(12L, 13L)));
        org.assertj.core.api.Assertions.assertThat(twice).containsExactly(12L);
    }

    @Test
    void s17KnowsWhereEachLaneKeepsItsState() {
        org.assertj.core.api.Assertions.assertThat(S17ContextRoutingTest.contextOf("order-query_OrderProjector_1_SUD_AMERICA"))
                .isEqualTo("SUD_AMERICA");
        org.assertj.core.api.Assertions.assertThat(S17ContextRoutingTest.expectedStateDb("order-query_OrderProjector_1_AUSTRALIA_NZ"))
                .isEqualTo("APAC");
        org.assertj.core.api.Assertions.assertThat(S17ContextRoutingTest.expectedStateDb("order-query_CatalogProjector_1_ALL"))
                .isEqualTo("consumers");
        org.assertj.core.api.Assertions.assertThat(S17ContextRoutingTest.expectedStateDb("order-observer_NotificationObserver_1_*"))
                .isEqualTo("EU");
        org.assertj.core.api.Assertions.assertThat(S17ContextRoutingTest.expectedStateDb("order-saga_PaymentSaga_1_AMERICA"))
                .isEqualTo("AM");
        var engines = S17ContextRoutingTest.expectedEngines();
        org.assertj.core.api.Assertions.assertThat(engines.get("order-query-eu"))
                .containsExactly("order-query_OrderProjector_1_AFRICA", "order-query_OrderProjector_1_EUROPA");
        org.assertj.core.api.Assertions.assertThat(engines.get("order-saga-apac")).hasSize(3);
        org.assertj.core.api.Assertions.assertThat(engines.get("order-query-global-apac"))
                .containsExactly("order-query_CatalogProjector_1_ALL");
        // one engine per region across the regional order-query deployments, plus ALL x2 replicas and *
        org.assertj.core.api.Assertions.assertThat(engines).hasSize(4 + 2 + 1 + 3 + 1);
    }
}
