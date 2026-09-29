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
}
