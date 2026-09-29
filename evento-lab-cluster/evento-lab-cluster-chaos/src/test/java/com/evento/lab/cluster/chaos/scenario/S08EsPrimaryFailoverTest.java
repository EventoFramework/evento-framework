package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.LatencyStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * S8: kill the event-store primary under load and promote a standby
 * ({@code S08_PROMOTE=es-pg-am}, the synchronous one, by default;
 * {@code es-pg-apac} for the asynchronous variant). The servers follow via the
 * multi-host URL; every advisory lock vanishes with the old primary (G3).
 *
 * <p>Destructive: replication cannot be re-established in place, so the whole
 * stack is recreated ({@code down -v} + {@code up --wait}) afterwards unless
 * {@code EVENTO_LAB_SKIP_RECREATE=true}.
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S08EsPrimaryFailoverTest extends AbstractScenario {

    @Override
    protected String id() {
        var target = System.getenv().getOrDefault("S08_PROMOTE", "es-pg-am");
        return target.equals("es-pg-am") ? "S08" : "S08-" + target;
    }

    @Override
    protected String title() {
        return "Event-store primary failover (promote " + System.getenv().getOrDefault("S08_PROMOTE", "es-pg-am") + ")";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G3", "G5", "G6", "G19");
    }

    @Test
    void esPrimaryFailover() throws Exception {
        scenario(() -> {
            var target = System.getenv().getOrDefault("S08_PROMOTE", "es-pg-am");
            var async = !target.equals("es-pg-am");
            report.param("promoted", target);
            report.param("replication", async ? "asynchronous" : "synchronous");
            onCleanup(() -> cluster.recreateStack());

            var killAt = new AtomicReference<Instant>();
            var promoteDelay = envSeconds("S08_PROMOTE_DELAY_S", 5);
            var killAfter = envSeconds("S08_KILL_AFTER_S", 30);
            var since = Instant.now();
            var records = load(LabConfig.REGIONS, env("S08_RATE", 20), 1, 0.5, envSeconds("S08_DURATION_S", 120),
                    elapsed -> {
                        if (killAt.get() == null && elapsed.compareTo(killAfter) >= 0) {
                            killAt.set(Instant.now());
                            cluster.kill("es-pg-eu");
                            report.event("killed es-pg-eu (ES primary)");
                            Cluster.sleep(promoteDelay);
                            var out = cluster.psqlMust(target, "evento", "SELECT pg_promote()");
                            report.event("promoted " + target + ": " + out.out().trim());
                        }
                    });
            var kill = killAt.get();
            if (kill == null) {
                report.harnessError("the kill never happened");
                return;
            }
            var firstAck = firstAckAfter(records, LabConfig.REGIONS, kill.plusSeconds(1));
            var outage = firstAck == null ? null : (firstAck.startMs() - kill.toEpochMilli()) / 1000.0;
            report.metric("writeOutageSeconds", outage);
            report.metric("afterKill", LatencyStats.of(filter(records, r -> r.isCommand()
                    && r.startMs() >= kill.toEpochMilli())));
            report.observe("writes resume after promotion",
                    "commands acknowledged again within 60 s (servers follow the multi-host URL)",
                    outage == null ? "no command acknowledged after the kill" : "first ack " + outage + " s after the kill",
                    outage != null && outage <= 60);

            markers(LabConfig.servers(), since, MARKERS);
            var results = checkInvariants(records, envSeconds("S08_CONVERGENCE_S", 180));
            var i1 = find(results, "I1");
            report.observe("no acknowledged event lost (I1)", i1.expected(),
                    async ? "async standby: acknowledged events not yet shipped are lost (operational limit)" : null,
                    i1.observed(), i1.passed(), async && !i1.passed(), null);
            var i2 = find(results, "I2");
            report.observe("no duplicate after ambiguous commits (I2)", i2.expected(),
                    "a client retry after an ambiguous commit duplicates (G5)", i2.observed(), i2.passed(),
                    !i2.passed(), "The load generator does not retry, so duplicates here come from inside the stack.");
            var i3 = find(results, "I3");
            report.observe("no fork although every advisory lock vanished (I3)", i3.expected(),
                    "locks lost with the old primary let two servers append from one story (G3, G6)",
                    i3.observed(), i3.passed(), !i3.passed(), null);

            // checkpoints above the new head: those consumers will never be delivered the gap
            try {
                var head = db.esHead();
                var above = new LinkedHashMap<String, Long>();
                db.checkpoints().forEach((k, v) -> {
                    if (v > head) above.put(k, v);
                });
                report.metric("esHeadAfterFailover", head);
                report.metric("checkpointsAboveHead", above);
                report.observe("no consumer checkpoint above the new ES head",
                        "every checkpoint <= head", async ? "async promotion: checkpoints can sit above the new head"
                                : null,
                        above.isEmpty() ? "none" : above.toString(), above.isEmpty(), async && !above.isEmpty(), null);
            } catch (Exception e) {
                report.harnessError("checkpoint read: " + e);
            }
        });
    }
}
