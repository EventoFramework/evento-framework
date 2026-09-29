package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.LabConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * S9: kill the app-data primary (consumer state, read models, side effects)
 * under load and promote {@code app-pg-am}. Consumer locks vanish, another
 * replica takes the lane. Checkpoints must never go backwards (G11); replays
 * are allowed and must be absorbed by the idempotent projections
 * ({@code stale_skips} counts them).
 *
 * <p>Destructive: the stack is recreated afterwards unless
 * {@code EVENTO_LAB_SKIP_RECREATE=true}.
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S09AppPrimaryFailoverTest extends AbstractScenario {

    @Override
    protected String id() {
        return "S09";
    }

    @Override
    protected String title() {
        return "App-data (consumer state) primary failover";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G11", "G25");
    }

    @Test
    void appPrimaryFailover() throws Exception {
        scenario(() -> {
            var target = System.getenv().getOrDefault("S09_PROMOTE", "app-pg-am");
            report.param("promoted", target);
            onCleanup(() -> cluster.recreateStack());

            // checkpoint time series, every 2 s, for every consumer
            var series = Collections.synchronizedList(new ArrayList<Map<String, Long>>());
            var times = Collections.synchronizedList(new ArrayList<Instant>());
            var stop = new AtomicBoolean(false);
            var sampler = Thread.ofVirtual().start(() -> {
                while (!stop.get()) {
                    try {
                        series.add(db.checkpoints());
                        times.add(Instant.now());
                    } catch (Exception ignored) {
                        // primary down / not promoted yet
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });

            var killAt = new AtomicReference<Instant>();
            var killAfter = envSeconds("S09_KILL_AFTER_S", 30);
            var promoteDelay = envSeconds("S09_PROMOTE_DELAY_S", 5);
            var since = Instant.now();
            var records = load(LabConfig.REGIONS, env("S09_RATE", 20), 1, 0.6, envSeconds("S09_DURATION_S", 120),
                    elapsed -> {
                        if (killAt.get() == null && elapsed.compareTo(killAfter) >= 0) {
                            killAt.set(Instant.now());
                            cluster.kill("app-pg-eu");
                            report.event("killed app-pg-eu (app-data primary)");
                            Cluster.sleep(promoteDelay);
                            var out = cluster.psqlMust(target, "app", "SELECT pg_promote()");
                            report.event("promoted " + target + ": " + out.out().trim());
                        }
                    });
            var results = checkInvariants(records, envSeconds("S09_CONVERGENCE_S", 240));
            stop.set(true);
            sampler.interrupt();

            // regressions: a consumer whose checkpoint is ever lower than an earlier sample
            var regressions = new TreeMap<String, String>();
            var max = new LinkedHashMap<String, Long>();
            var maxAt = new LinkedHashMap<String, Instant>();
            synchronized (series) {
                for (int i = 0; i < series.size(); i++) {
                    var t = times.get(i);
                    for (var e : series.get(i).entrySet()) {
                        var prev = max.get(e.getKey());
                        if (prev != null && e.getValue() < prev) {
                            regressions.putIfAbsent(e.getKey(), prev + " (at " + maxAt.get(e.getKey()) + ") -> "
                                    + e.getValue() + " (at " + t + ")");
                        }
                        if (prev == null || e.getValue() > prev) {
                            max.put(e.getKey(), e.getValue());
                            maxAt.put(e.getKey(), t);
                        }
                    }
                }
            }
            report.metric("checkpointSamples", series.size());
            report.metric("checkpointRegressions", regressions);
            report.observe("checkpoints never go backwards",
                    "last_sequence is monotonic for every consumer across the failover",
                    "a stale lane holder or the promoted standby rewinds a checkpoint (G11)",
                    regressions.isEmpty() ? "monotonic over " + series.size() + " samples" : regressions.toString(),
                    regressions.isEmpty(), !regressions.isEmpty(),
                    "A rewind to a value the async/sync standby had is expected only if the promoted node lagged.");

            var kill = killAt.get();
            if (kill != null) {
                // do lanes resume? compare the last sample with the one at the kill
                Map<String, Long> atKill = null;
                synchronized (series) {
                    for (int i = 0; i < series.size(); i++) {
                        if (!times.get(i).isAfter(kill)) atKill = series.get(i);
                    }
                    var last = series.isEmpty() ? Map.<String, Long>of() : series.getLast();
                    var stuck = new ArrayList<String>();
                    if (atKill != null) {
                        for (var e : atKill.entrySet()) {
                            if (last.getOrDefault(e.getKey(), -1L) <= e.getValue()) stuck.add(e.getKey());
                        }
                    }
                    report.observe("consumer lanes resume on the promoted primary",
                            "every consumer's checkpoint advances after the failover",
                            stuck.isEmpty() ? "all advanced" : "not advanced: " + stuck, stuck.isEmpty());
                }
            }
            var i2 = find(results, "I2");
            report.observe("replays absorbed idempotently (I2)", i2.expected(), i2.observed(), i2.passed());
            try (var c = db.app(); var st = c.createStatement(); var rs = st.executeQuery(
                    "SELECT coalesce(sum(stale_skips),0) FROM order_view_global WHERE order_id LIKE '"
                            + prefix.replace("'", "") + "%'")) {
                rs.next();
                report.metric("globalViewReplaysAbsorbed", rs.getLong(1));
            } catch (Exception e) {
                report.harnessError("stale_skips read: " + e);
            }
            markers(List.of("cell-core-eu", "cell-core-am", "cell-core-apac", "saga-eu", "saga-apac"), since,
                    List.of("event=lock_release_failed", "PSQLException", "Connection refused", "OptimisticLock",
                            "evento_v2_consumer_state"));
        });
    }
}
