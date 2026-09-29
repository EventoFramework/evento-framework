package com.evento.lab.cluster.chaos;

import java.time.Duration;
import java.util.List;

/**
 * Standalone invariant check against the running stack, outside any scenario:
 *
 * <pre>
 *   ./gradlew :evento-lab-cluster:evento-lab-cluster-chaos:checkInvariants -Pprefix=S00- -Plabel=manual
 *   java -cp ... com.evento.lab.cluster.chaos.InvariantCheckerMain [orderIdPrefix] [label] [convergenceSeconds]
 * </pre>
 *
 * Without a client ledger there is nothing to compare acknowledgements with,
 * so I1 checks nothing and I8 is empty; I2..I7 run on every order whose id
 * starts with the prefix (empty prefix = every order). Exit code 0 when no
 * invariant FAILs or ERRORs, 1 otherwise.
 */
public final class InvariantCheckerMain {

    private InvariantCheckerMain() {
    }

    public static void main(String[] args) {
        var prefix = args.length > 0 ? args[0] : "";
        var label = args.length > 1 && !args[1].isBlank() ? args[1] : "run";
        var seconds = args.length > 2 ? Integer.parseInt(args[2]) : 30;
        var config = LabConfig.fromEnvironment();
        var db = new Db(config);
        var report = new ScenarioReport("invariants", "Standalone invariant check", List.of(), label);
        report.param("prefix", prefix);
        report.param("convergenceTimeoutSeconds", seconds);
        report.note("No client ledger: I1 has no acknowledged commands to check and I8 has no latencies.");
        report.invariants(Invariants.checkAll(() -> db.snapshot(prefix), List.of(), Duration.ofSeconds(seconds)));
        var snap = db.snapshot(prefix);
        if (!snap.loadErrors().isEmpty()) report.harnessError("load errors: " + snap.loadErrors());
        report.metric("events", snap.events() == null ? null : snap.events().size());
        report.metric("esHead", snap.esHead());
        report.finish();
        new ResultWriter(config.resultsDir()).write(report);
        boolean bad = report.invariants().stream().anyMatch(i -> i.status() == InvariantResult.Status.FAIL
                || i.status() == InvariantResult.Status.ERROR);
        System.exit(bad ? 1 : 0);
    }
}
