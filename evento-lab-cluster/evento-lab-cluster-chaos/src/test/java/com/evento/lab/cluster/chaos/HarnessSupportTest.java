package com.evento.lab.cluster.chaos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The pure parts of the harness: stats, ledger, marker counting, result files. */
class HarnessSupportTest {

    private static RequestRecord cmd(String region, long latency, RequestRecord.Outcome o, String kind) {
        return new RequestRecord(0, latency, region, "create", "o", o == RequestRecord.Outcome.ACK ? 201 : 502, o,
                kind, null, null, null);
    }

    @Test
    void percentilesUseNearestRank() {
        var sorted = new java.util.ArrayList<Long>();
        for (long i = 1; i <= 100; i++) sorted.add(i);
        assertThat(LatencyStats.pct(sorted, 50)).isEqualTo(50);
        assertThat(LatencyStats.pct(sorted, 99)).isEqualTo(99);
        assertThat(LatencyStats.pct(List.of(7L), 99)).isEqualTo(7);
        assertThat(LatencyStats.pct(List.of(), 50)).isZero();
    }

    @Test
    void latencyStatsCountOutcomesPerRegion() {
        var records = List.of(
                cmd("EUROPA", 10, RequestRecord.Outcome.ACK, "ok"),
                cmd("EUROPA", 30_000, RequestRecord.Outcome.ERROR, "timeout"),
                cmd("CINA", 20, RequestRecord.Outcome.ACK, "ok"),
                new RequestRecord(0, 5, "CINA", "get_global", "o", 200, RequestRecord.Outcome.ACK, "ok", null, null, null));
        var by = LatencyStats.byRegion(records);
        assertThat(by).containsOnlyKeys("CINA", "EUROPA");
        assertThat(by.get("EUROPA").failed()).isEqualTo(1);
        assertThat(by.get("EUROPA").kinds()).containsEntry("timeout", 1);
        assertThat(by.get("EUROPA").max()).isEqualTo(30_000);
        assertThat(by.get("CINA").count()).isEqualTo(1); // reads are not commands
    }

    @Test
    void ledgerSeparatesAcknowledgedFromUnknown() {
        var ledgers = OrderLedger.fromRecords(List.of(
                new RequestRecord(0, 1, "EUROPA", "create", "o1", 201, RequestRecord.Outcome.ACK, "ok", null, null, null),
                new RequestRecord(0, 1, "EUROPA", "add_item", "o1", 504, RequestRecord.Outcome.ERROR, "timeout", null, null, null),
                new RequestRecord(0, 1, "EUROPA", "add_item", "o1", 409, RequestRecord.Outcome.REJECTED, "rejected", null, null, null),
                new RequestRecord(0, 1, "EUROPA", "complete", "o1", 0, RequestRecord.Outcome.NO_RESPONSE, "client_timeout", null, null, null)));
        var l = ledgers.get("o1");
        assertThat(l.createAcked).isEqualTo(1);
        assertThat(l.itemsAcked).isZero();
        assertThat(l.itemsIndeterminate).isEqualTo(1);
        assertThat(l.completeIndeterminate).isEqualTo(1);
    }

    @Test
    void markersAreCountedAndGrepped() {
        var logs = """
                2026 INFO event=handshake_complete server_instance=evento-eu
                2026 WARN duplicate key value violates unique constraint "core__bundle_pkey"
                2026 INFO event=handshake_complete server_instance=evento-am
                """;
        var counts = Cluster.countMarkers(logs, List.of("event=handshake_complete", "duplicate key", "no handler for"));
        assertThat(counts).containsEntry("event=handshake_complete", 2).containsEntry("duplicate key", 1)
                .containsEntry("no handler for", 0);
        assertThat(Cluster.grep(logs, "core__bundle_pkey", 5)).hasSize(1);
    }

    @Test
    void likePrefixEscapesMetacharacters() {
        assertThat(Db.likePrefix("s11-run-a_b%")).isEqualTo("s11-run-a\\_b\\%%");
    }

    @Test
    void resultWriterProducesSummaryAndReport(@TempDir Path dir) throws Exception {
        var report = new ScenarioReport("S99", "Test scenario", List.of("G1"), "before/1");
        report.param("rate", 20);
        report.event("fault injected");
        report.observe("a thing", "works", "breaks (G1)", "broke | badly", false, true, "a note");
        report.observe("other", "works", "works", true);
        report.invariants(List.of(InvariantResult.of("I1", "no lost events", "none lost", "1 lost",
                java.util.Map.of("lostOps", 1), List.of("o1: lost"))));
        report.finish();
        var out = new ResultWriter(dir).write(report);
        assertThat(out).isEqualTo(dir.resolve("S99").resolve("before_1"));
        var md = Files.readString(out.resolve("report.md"));
        assertThat(md).contains("# S99: Test scenario").contains("KNOWN_DEFECT_REPRODUCED").contains("AS_EXPECTED")
                .contains("broke \\| badly").contains("o1: lost");
        var json = Json.read(Files.readString(out.resolve("summary.json")));
        assertThat(json.path("scenario").asText()).isEqualTo("S99");
        assertThat(json.path("verdictCounts").path("KNOWN_DEFECT_REPRODUCED").asInt()).isEqualTo(1);
        assertThat(json.path("invariants").get(0).path("violationCount").asInt()).isEqualTo(1);
    }

    @Test
    void labConfigMapsRegionsToServicesAndTables() {
        assertThat(LabConfig.regionService("SUD_AMERICA")).isEqualTo("region-sud-america");
        assertThat(LabConfig.regionTable("AUSTRALIA_NZ")).isEqualTo("order_view_australia_nz");
        assertThat(LabConfig.regionsOfCell("APAC")).containsExactly("CINA", "INDIA", "AUSTRALIA_NZ");
        assertThat(LabConfig.appServices()).hasSize(12).contains("region-australia-nz", "saga-apac");
    }
}
