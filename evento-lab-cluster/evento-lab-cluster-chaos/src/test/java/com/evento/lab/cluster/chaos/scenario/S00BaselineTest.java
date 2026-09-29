package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.Invariants;
import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.LatencyStats;
import com.evento.lab.cluster.chaos.RequestRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;

/**
 * S0: full topology, no fault, every region creating orders through the LB.
 * The reference run: I1..I7 must hold; latency and ES append throughput are
 * the baseline for G9 (CPES global lock round trips).
 *
 * <p>Knobs: {@code S00_RATE} (orders/min/region, 20), {@code S00_DURATION_S} (60),
 * {@code S00_ITEMS} (2), {@code S00_COMPLETE_RATIO} (0.5).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S00BaselineTest extends AbstractScenario {

    @Override
    protected String id() {
        return "S00";
    }

    @Override
    protected String title() {
        return "Baseline, no faults";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G9 (reference)");
    }

    @Test
    void baseline() throws Exception {
        scenario(() -> {
            var duration = envSeconds("S00_DURATION_S", 60);
            var headBefore = db.esHead();
            var records = load(LabConfig.REGIONS, env("S00_RATE", 20), LabConfig.envInt("S00_ITEMS", 2),
                    env("S00_COMPLETE_RATIO", 0.5), duration, null);
            var headAfter = db.esHead();

            var stats = LatencyStats.of(records);
            report.metric("latencyOverall", stats);
            report.metric("latencyByRegion", LatencyStats.byRegion(records));
            var appended = headAfter - headBefore;
            report.metric("esEventsAppended", appended);
            report.metric("esAppendsPerSecond", appended / (double) Math.max(1, duration.toSeconds()));
            report.metric("ackedCommandsPerSecond", stats.acked() / (double) Math.max(1, duration.toSeconds()));

            report.observe("every command acknowledged",
                    "100% of commands 2xx with no fault injected",
                    stats.acked() + "/" + stats.count() + " acknowledged, failures " + stats.kinds(),
                    stats.failed() == 0);

            var results = checkInvariants(records, envSeconds("S00_CONVERGENCE_S", 90));
            for (var inv : results) {
                if (inv.id().equals("I8")) continue;
                report.observe(inv.id() + " " + inv.name(), inv.expected(), inv.observed(), inv.passed());
            }

            // saga end to end: every completed order gets its payment
            var paid = Cluster.waitUntil(Duration.ofSeconds(60), Duration.ofSeconds(3), () -> {
                var ev = db.snapshot(prefix).events();
                if (ev == null) return false;
                var exp = Invariants.expectedOrders(ev).values();
                return exp.stream().filter(e -> e.completed() > 0).allMatch(e -> e.paid() > 0);
            });
            var ev = db.snapshot(prefix).events();
            long completed = ev == null ? -1 : Invariants.expectedOrders(ev).values().stream()
                    .filter(e -> e.completed() > 0).count();
            long payments = ev == null ? -1 : Invariants.expectedOrders(ev).values().stream()
                    .filter(e -> e.paid() > 0).count();
            report.observe("payment saga completes every order",
                    "one PaymentProcessed per completed order within 60 s",
                    payments + " payments for " + completed + " completed orders", paid);
            report.metric("commandsByOutcome", records.stream().filter(RequestRecord::isCommand)
                    .collect(java.util.stream.Collectors.groupingBy(r -> r.outcome().name(),
                            java.util.stream.Collectors.counting())));
        });
    }
}
