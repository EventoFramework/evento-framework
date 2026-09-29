package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.LatencyStats;
import com.evento.lab.cluster.chaos.RequestRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * S2: two handler replicas in one cell ({@code cell-core-eu} + the
 * {@code replica}-profile {@code cell-core-eu-b}); kill one under load. The
 * server picks among available nodes, so new commands must land on eu-b;
 * commands in flight on the killed node fail ({@code peer disconnected}).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S02KillHandlerReplicaTest extends AbstractScenario {

    private static final List<String> EU = LabConfig.regionsOfCell("EU");

    @Override
    protected String id() {
        return "S02";
    }

    @Override
    protected String title() {
        return "Kill one handler replica inside a cell";
    }

    @Override
    protected List<String> gaps() {
        return List.of("intra-server failover baseline");
    }

    @Test
    void killHandlerReplica() throws Exception {
        scenario(() -> {
            onCleanup(() -> {
                cluster.ensureRunning("cell-core-eu");
                cluster.waitReady("cell-core-eu", Duration.ofSeconds(300));
                cluster.removeService("replica", "cell-core-eu-b");
            });
            cluster.upProfileService("replica", "cell-core-eu-b");
            var ready = cluster.waitReady("cell-core-eu-b", Duration.ofSeconds(300));
            report.event("cell-core-eu-b ready=" + ready);
            if (!ready) report.harnessError("cell-core-eu-b did not become ready");

            var killAt = new AtomicReference<Instant>();
            var killAfter = envSeconds("S02_KILL_AFTER_S", 20);
            var since = Instant.now();
            var records = load(EU, env("S02_RATE", 60), 2, 0.5, envSeconds("S02_DURATION_S", 70), elapsed -> {
                if (killAt.get() == null && elapsed.compareTo(killAfter) >= 0) {
                    killAt.set(Instant.now());
                    cluster.kill("cell-core-eu");
                    report.event("killed cell-core-eu");
                }
            });
            if (killAt.get() == null) {
                report.harnessError("the kill never happened");
                return;
            }
            var kill = killAt.get();
            var inFlight = inFlightAt(records, kill);
            var after = filter(records, r -> r.isCommand() && r.startMs() >= kill.plusSeconds(5).toEpochMilli());
            var inFlightFailed = filter(inFlight, r -> r.outcome() != RequestRecord.Outcome.ACK);
            report.metric("inFlightAtKill", inFlight.size());
            report.metric("inFlightFailedKinds", kinds(inFlightFailed));
            report.metric("latencyAfterKill", LatencyStats.of(after));
            report.metric("latencyBeforeKill", LatencyStats.of(filter(records,
                    r -> r.isCommand() && r.startMs() + r.latencyMs() < kill.toEpochMilli())));

            var rateAfter = successRate(after);
            report.observe("new commands go to the surviving replica",
                    "commands sent >= 5 s after the kill are acknowledged (RANDOM pick among available nodes)",
                    String.format("%.1f%% of %d acknowledged; failures %s", rateAfter * 100, after.size(), kinds(after)),
                    !Double.isNaN(rateAfter) && rateAfter >= 0.99);
            var slowFailures = inFlightFailed.stream().filter(r -> r.latencyMs() > 25_000).count();
            report.observe("in-flight commands on the killed replica",
                    "fail promptly with 'peer disconnected' (the outcome is indeterminate)",
                    null,
                    inFlightFailed.size() + " of " + inFlight.size() + " failed, kinds " + kinds(inFlightFailed)
                            + ", " + slowFailures + " only after > 25 s",
                    inFlightFailed.isEmpty() || slowFailures == 0, null,
                    "A failure that surfaces only after ~30 s is the request-timeout path, not a disconnect notification.");

            var results = checkInvariants(records, envSeconds("S02_CONVERGENCE_S", 120));
            report.observe("I1 holds for acknowledged orders", "no acknowledged command lost",
                    find(results, "I1").observed(), find(results, "I1").passed());
            markers(List.of("evento-eu", "cell-core-eu-b"), since, MARKERS);
        });
    }
}
