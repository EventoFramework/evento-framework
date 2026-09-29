package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.LatencyStats;
import com.evento.lab.cluster.chaos.RequestRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S6: sweep the RTT between the AM/APAC servers and the ES primary
 * (0, 50, 150, 300 ms by default) and measure throughput and latency at each
 * step. evento-eu talks to the primary on its LAN, so the EU cell is the
 * control. Before G9 the CPES append costs ~4 WAN round trips under a global
 * lock: cluster-wide throughput ~ 1/(4 RTT) for the remote cells.
 *
 * <p>Knobs: {@code S06_RTTS_MS} ("0,50,150,300"), {@code S06_STEP_S} (40),
 * {@code S06_RATE} (30 orders/min/region).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S06WanLatencySweepTest extends AbstractScenario {

    @Override
    protected String id() {
        return "S06";
    }

    @Override
    protected String title() {
        return "WAN latency sweep to the ES primary";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G9", "G18");
    }

    @Test
    void wanLatencySweep() throws Exception {
        scenario(() -> {
            var rtts = Arrays.stream(System.getenv().getOrDefault("S06_RTTS_MS", "0,50,150,300").split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();
            var proxies = toxi.towards("es-pg-eu");
            report.param("proxiesTowardsEsPrimary", proxies.stream().map(p -> p.name()).toList());
            report.param("rttsMs", rtts);
            if (proxies.isEmpty()) {
                report.harnessError("no toxiproxy proxy with upstream es-pg-eu");
                return;
            }
            var allRecords = new ArrayList<RequestRecord>();
            var curve = new LinkedHashMap<String, Object>();
            var hasBaseline = proxies.stream()
                    .allMatch(p -> p.toxics().stream().anyMatch(t -> t.name().startsWith("wan-latency")));
            report.param("wanBaselinePresent", hasBaseline);
            for (var rtt : rtts) {
                if (hasBaseline) {
                    for (var p : proxies) toxi.setBaselineLatency(p.name(), rtt / 2, rtt == 0 ? 0 : 5);
                } else {
                    toxi.heal();
                    if (rtt > 0) for (var p : proxies) toxi.addLatency(p.name(), "chaos-sweep", rtt / 2, 5);
                }
                report.event("RTT to ES primary = " + rtt + " ms");
                var step = envSeconds("S06_STEP_S", 40);
                long headBefore = db.esHead();
                var recs = load(LabConfig.REGIONS, env("S06_RATE", 30), 1, 0.0, step, null);
                long headAfter = db.esHead();
                allRecords.addAll(recs);
                var point = new LinkedHashMap<String, Object>();
                point.put("esAppendsPerSecond", (headAfter - headBefore) / (double) step.toSeconds());
                for (var cell : LabConfig.CELLS) {
                    var cellRecs = filter(recs, r -> LabConfig.regionsOfCell(cell).contains(r.region()));
                    var s = LatencyStats.of(cellRecs);
                    point.put(cell, Map.of("ackedPerSecond", s.acked() / (double) step.toSeconds(),
                            "p50", s.p50(), "p99", s.p99(), "failed", s.failed()));
                }
                point.put("predictedPre-G9RemoteCellAppendsPerSecond", rtt == 0 ? null : 1000.0 / (4.0 * rtt));
                curve.put(rtt + "ms", point);
                report.event("RTT " + rtt + " ms: " + point);
            }
            report.metric("throughputCurve", curve);
            toxi.heal();
            report.event("WAN baseline restored");

            var results = checkInvariants(allRecords, envSeconds("S06_CONVERGENCE_S", 180));
            for (var id : List.of("I1", "I2", "I3", "I4", "I5", "I6")) {
                var inv = find(results, id);
                report.observe(id + " across the sweep", inv.expected(), inv.observed(), inv.passed());
            }
            report.note("Compare the AM/APAC p50 per step with the RTT: ~4 RTT per append before G9, ~1-2 RTT after.");
        });
    }
}
