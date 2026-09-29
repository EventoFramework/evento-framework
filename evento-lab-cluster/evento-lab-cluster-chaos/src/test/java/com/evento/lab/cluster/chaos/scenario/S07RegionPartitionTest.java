package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.LatencyStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * S7: cut the APAC cell off the WAN for 60 s, silently (no RST): the cell
 * network is detached from toxiproxy (every cross-cell link) and from the lb
 * (bus VIP backups and HTTP ingress). Inside the cell evento-apac and its
 * bundles still see each other, but evento-apac loses the ES primary and the
 * APAC JVMs lose the app-DB primary.
 *
 * <p>Watched: whether the global consumer lanes (held by whichever JVM won
 * them, possibly an APAC one) keep moving (G25: an orphaned advisory lock
 * holds a lane until Postgres keepalive kills the backend), and after
 * healing I6 plus I2 on the side effects (G11).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S07RegionPartitionTest extends AbstractScenario {

    private static final List<String> NON_APAC = List.of("EUROPA", "AFRICA", "AMERICA", "SUD_AMERICA");
    private static final List<String> GLOBAL_LANES = List.of("cluster-query-global_GlobalOrderProjector_",
            "cluster-saga_PaymentSaga_", "cluster-saga_ClusterEventObserver_");

    @Override
    protected String id() {
        return "S07";
    }

    @Override
    protected String title() {
        return "Region partition (APAC off the WAN)";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G22", "G25", "G11");
    }

    @Test
    void regionPartition() throws Exception {
        scenario(() -> {
            var partitionFor = envSeconds("S07_PARTITION_S", 60);
            var startAfter = envSeconds("S07_START_AFTER_S", 20);
            var detached = Collections.synchronizedList(new ArrayList<Cluster.Detachment>());
            onCleanup(() -> {
                heal(detached);
            });

            // checkpoint time series of the global lanes
            var samples = Collections.synchronizedList(new ArrayList<Map<String, Object>>());
            var stop = new AtomicBoolean(false);
            var sampler = Thread.ofVirtual().start(() -> {
                while (!stop.get()) {
                    var row = new LinkedHashMap<String, Object>();
                    row.put("t", Instant.now().toString());
                    try {
                        db.checkpoints().forEach((k, v) -> {
                            if (GLOBAL_LANES.stream().anyMatch(k::startsWith)) row.put(k, v);
                        });
                    } catch (Exception e) {
                        row.put("error", e.getMessage());
                    }
                    samples.add(row);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });

            var cutAt = new AtomicReference<Instant>();
            var healAt = new AtomicReference<Instant>();
            var since = Instant.now();
            var records = load(LabConfig.REGIONS, env("S07_RATE", 20), 1, 0.6,
                    startAfter.plus(partitionFor).plus(envSeconds("S07_AFTER_HEAL_S", 40)), elapsed -> {
                        if (cutAt.get() == null && elapsed.compareTo(startAfter) >= 0) {
                            cutAt.set(Instant.now());
                            detached.add(cluster.disconnect("net-apac", "toxiproxy"));
                            detached.add(cluster.disconnect("net-apac", "lb"));
                            report.event("APAC partitioned (net-apac detached from toxiproxy and lb)");
                        } else if (cutAt.get() != null && healAt.get() == null
                                && Duration.between(cutAt.get(), Instant.now()).compareTo(partitionFor) >= 0) {
                            healAt.set(Instant.now());
                            heal(detached);
                            report.event("partition healed");
                        }
                    });
            Cluster.sleep(Duration.ofSeconds(10));
            stop.set(true);
            sampler.interrupt();
            report.attach("global lane checkpoints (every 5 s)", samples.stream().map(Object::toString).toList());
            if (cutAt.get() == null || healAt.get() == null) {
                report.harnessError("partition did not complete: cut=" + cutAt.get() + " heal=" + healAt.get());
                return;
            }
            var cut = cutAt.get();
            var heal = healAt.get();

            var during = filter(records, r -> r.isCommand() && r.startMs() >= cut.toEpochMilli()
                    && r.startMs() < heal.toEpochMilli());
            var nonApacDuring = filter(during, r -> NON_APAC.contains(r.region()));
            var apacDuring = filter(during, r -> !NON_APAC.contains(r.region()));
            report.metric("nonApacDuringPartition", LatencyStats.of(nonApacDuring));
            report.metric("apacDuringPartition", LatencyStats.of(apacDuring));
            var rate = successRate(nonApacDuring);
            report.observe("EU and AM keep working during the APAC partition",
                    ">= 95% of EU/AM commands acknowledged while APAC is cut off",
                    String.format("%.1f%% of %d; failures %s", rate * 100, nonApacDuring.size(), kinds(nonApacDuring)),
                    !Double.isNaN(rate) && rate >= 0.95);

            // longest period during the partition in which a global lane did not move while events kept coming
            var stall = longestStallSeconds(samples, cut, heal);
            report.metric("globalLaneLongestStallDuringPartitionSeconds", stall);
            report.observe("global consumer lanes during the partition",
                    "a lane held by a cut-off JVM is released within ~30 s (keepalive) and taken over",
                    "the lane stays stuck on an orphaned advisory lock until Postgres kills the backend (G25)",
                    "longest checkpoint stall per lane (s): " + stall,
                    stall.values().stream().allMatch(s -> s <= 35), stall.values().stream().anyMatch(s -> s > 35),
                    "A stall also happens legitimately when no new event arrives; load keeps producing EU/AM events.");

            var appMarkers = markers(S03KillServerTest.APAC_JVMS, since, MARKERS);
            report.metric("apacSupervisorSessionFailures", total(appMarkers, "event=supervisor_session_failed"));

            var results = checkInvariants(records, envSeconds("S07_CONVERGENCE_S", 240));
            var i6 = find(results, "I6");
            report.observe("I6 convergence after healing", i6.expected(), i6.observed(), i6.passed());
            var i2 = find(results, "I2");
            report.observe("no duplicate projection/saga side effects (I2)", i2.expected(),
                    "two replicas process one lane after the partition; the stale one re-applies (G11)",
                    i2.observed(), i2.passed(), !i2.passed(), null);
        });
    }

    /** Re-attach everything; the lb must be restarted to bind its VIP address on the network again. */
    private void heal(List<Cluster.Detachment> detached) {
        boolean lb = false;
        for (var d : List.copyOf(detached)) {
            try {
                cluster.reconnect(d);
                lb |= d.container().equals("lb");
            } catch (Exception e) {
                report.harnessError("reconnect " + d + ": " + e);
            }
        }
        detached.clear();
        if (lb) cluster.restart("lb");
    }

    /** For each lane: the longest run of identical checkpoint values within [cut, heal], in seconds. */
    static Map<String, Long> longestStallSeconds(List<Map<String, Object>> samples, Instant cut, Instant heal) {
        var out = new LinkedHashMap<String, Long>();
        var lastValue = new LinkedHashMap<String, Object>();
        var since = new LinkedHashMap<String, Instant>();
        for (var s : samples) {
            var t = Instant.parse((String) s.get("t"));
            if (t.isBefore(cut) || t.isAfter(heal)) continue;
            for (var e : s.entrySet()) {
                if (e.getKey().equals("t") || e.getKey().equals("error")) continue;
                var prev = lastValue.get(e.getKey());
                if (prev == null || !prev.equals(e.getValue())) {
                    lastValue.put(e.getKey(), e.getValue());
                    since.put(e.getKey(), t);
                }
                var d = Duration.between(since.get(e.getKey()), t).toSeconds();
                out.merge(e.getKey(), d, Math::max);
            }
        }
        return out;
    }
}
