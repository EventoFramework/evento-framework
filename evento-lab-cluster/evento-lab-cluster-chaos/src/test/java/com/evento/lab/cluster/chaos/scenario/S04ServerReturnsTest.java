package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.InvariantResult;
import com.evento.lab.cluster.chaos.Invariants;
import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.RequestRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S4: a server comes back after its bundles failed over, in two variants:
 * a restart after a kill, and a half-closed socket ({@code docker pause}
 * longer than the 45 s read-idle, then unpause). The old server's
 * {@code NodeLeft} must not unregister bundles that now live elsewhere (G7);
 * I7 is checked after each variant.
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S04ServerReturnsTest extends AbstractScenario {

    private static final List<String> APAC = LabConfig.regionsOfCell("APAC");

    @Override
    protected String id() {
        return "S04";
    }

    @Override
    protected String title() {
        return "Server returns after failover";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G7", "G17");
    }

    @Test
    void serverReturns() throws Exception {
        scenario(() -> {
            onCleanup(() -> cluster.ensureRunning("evento-apac"));
            var since = Instant.now();
            var outage = envSeconds("S04_OUTAGE_S", 45);
            var settle = envSeconds("S04_SETTLE_S", 60);

            // --- variant A: kill, bundles move (if they can), restart --------------------------------
            report.event("variant A: kill + restart");
            cluster.kill("evento-apac");
            Cluster.sleep(outage);
            var duringOutage = probe("A-outage");
            cluster.start("evento-apac");
            cluster.waitReady("evento-apac", Duration.ofSeconds(420));
            report.event("evento-apac back; settling " + settle.toSeconds() + " s (> 45 s read-idle)");
            Cluster.sleep(settle);
            var afterA = probe("A-after");
            var catalogA = catalog("A");
            report.observe("A: catalog after the killed server returns",
                    "every bundle, handler and consumer still registered (I7)",
                    "the returning/old server's NodeLeft clears the moved bundle's rows (G7)",
                    catalogA.observed(), catalogA.passed(), !catalogA.passed(), null);
            report.observe("A: APAC commands after the return",
                    "acknowledged", ackSummary(afterA), allAck(afterA));
            report.metric("A.probesDuringOutage", ackSummary(duringOutage));

            // --- variant B: pause longer than read-idle, unpause -------------------------------------
            report.event("variant B: pause " + envSeconds("S04_PAUSE_S", 60).toSeconds() + " s");
            cluster.pause("evento-apac");
            onCleanup(() -> {
                if (cluster.state("evento-apac").equals("paused")) cluster.unpause("evento-apac");
            });
            Cluster.sleep(envSeconds("S04_PAUSE_S", 60));
            var duringPause = probe("B-paused");
            cluster.unpause("evento-apac");
            report.event("unpaused; settling " + settle.toSeconds() + " s");
            Cluster.sleep(settle);
            var afterB = probe("B-after");
            var catalogB = catalog("B");
            report.observe("B: catalog after a half-closed socket heals",
                    "every bundle, handler and consumer still registered (I7)",
                    "the stale session's NodeLeft wipes a bundle that reconnected elsewhere (G7)",
                    catalogB.observed(), catalogB.passed(), !catalogB.passed(), null);
            report.observe("B: APAC commands after unpause", "acknowledged", ackSummary(afterB), allAck(afterB));
            report.metric("B.probesDuringPause", ackSummary(duringPause));

            var m = markers(List.of("evento-eu", "evento-am", "evento-apac"), since, MARKERS);
            report.metric("unregisterCalls", total(m, "[BundleService] Unregister called for bundleId="));
            report.metric("skippedUnregisters", total(m, "Skipping unregister of bundle"));
            report.note("G17: the GUI/admin views of each server only show locally connected bundles; "
                    + "compare http://localhost:13000..13002 by hand when reading this result.");
        });
    }

    private List<RequestRecord> probe(String label) {
        var out = new ArrayList<RequestRecord>();
        for (var r : APAC) {
            out.add(gateway.create(r, gateway.newOrderId(prefix + label.toLowerCase() + "-", r),
                    UUID.randomUUID().toString()));
        }
        report.event("probe " + label + ": " + ackSummary(out));
        return out;
    }

    private InvariantResult catalog(String variant) {
        var snap = db.snapshot(prefix);
        var r = Invariants.i7Catalog(snap.catalog(), LabConfig.EXPECTED_BUNDLES, LabConfig.EXPECTED_HANDLED_PAYLOADS,
                Invariants.expectedConsumerPrefixes());
        var renamed = new InvariantResult("I7-" + variant, r.name() + " (variant " + variant + ")", r.status(),
                r.expected(), r.observed(), r.metrics(), r.violationCount(), r.violations());
        report.invariants(List.of(renamed));
        return renamed;
    }

    private static boolean allAck(List<RequestRecord> rs) {
        return rs.stream().allMatch(r -> r.outcome() == RequestRecord.Outcome.ACK);
    }

    private static String ackSummary(List<RequestRecord> rs) {
        return rs.stream().filter(r -> r.outcome() == RequestRecord.Outcome.ACK).count() + "/" + rs.size()
                + " acknowledged " + kinds(rs);
    }
}
