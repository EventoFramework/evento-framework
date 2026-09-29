package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.LabConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * S10: the three evento-servers start at the same instant on an empty event
 * store, N times. Each server runs {@code schema.sql} on boot
 * ({@code spring.sql.init.mode=always}); concurrent DDL can fail with
 * {@code pg_type_typname_nsp_index} / {@code tuple concurrently updated} (G14).
 *
 * <p>Each iteration: stop the servers, drop and recreate the {@code public}
 * schema of the ES primary, start all three in parallel (no health chain),
 * wait for healthy-or-dead. Afterwards the consumer state and read models of
 * every cell database and of the {@code consumers} database are reset (the
 * event store restarted from sequence 1) and the bundle JVMs restarted.
 *
 * <p>Knobs: {@code S10_ITERATIONS} (3; the assessment suggests 10).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S10ColdStartTest extends AbstractScenario {

    private static final List<String> SCHEMA_RACE = List.of("pg_type_typname_nsp_index", "tuple concurrently updated",
            "duplicate key", "ScriptStatementFailedException", "APPLICATION FAILED TO START", "deadlock detected");

    private static final List<String> STATE_TABLES = List.of("evento_v2_consumer_state", "evento_v2_dedupe",
            "evento_v2_saga_state", "evento_v2_dead_event");
    private static final List<String> APP_TABLES = List.of("observed_events", "payments", "order_view",
            "order_view_global", "catalog_price");

    @Override
    protected String id() {
        return "S10";
    }

    @Override
    protected String title() {
        return "Cold start of 3 servers on an empty event store";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G14");
    }

    @Test
    void coldStart() throws Exception {
        scenario(() -> {
            var iterations = LabConfig.envInt("S10_ITERATIONS", 3);
            report.param("iterations", iterations);
            onCleanup(this::resetAppState);
            var perIteration = new ArrayList<Map<String, Object>>();
            int failedIterations = 0;
            int raceMarkers = 0;
            for (int i = 1; i <= iterations; i++) {
                report.event("iteration " + i + ": stop servers");
                parallel(LabConfig.servers(), cluster::stop);
                cluster.psqlMust("es-pg-eu", "evento", "DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
                var since = Instant.now();
                report.event("iteration " + i + ": start the 3 servers together on an empty schema");
                parallel(LabConfig.servers(), cluster::start);
                var outcome = new LinkedHashMap<String, Object>();
                boolean allUp = true;
                for (var s : LabConfig.servers()) {
                    var t0 = Instant.now();
                    var up = Cluster.waitUntil(Duration.ofSeconds(LabConfig.envInt("S10_BOOT_TIMEOUT_S", 360)),
                            Duration.ofSeconds(2), () -> {
                                var st = cluster.state(s);
                                return cluster.isReady(s) || st.equals("exited") || st.equals("dead")
                                        || cluster.health(s).equals("unhealthy");
                            }) && cluster.isReady(s);
                    var logs = cluster.logsSince(s, since);
                    var counts = Cluster.countMarkers(logs, SCHEMA_RACE);
                    counts.values().removeIf(v -> v == 0);
                    raceMarkers += counts.values().stream().mapToInt(Integer::intValue).sum();
                    var excerpt = new ArrayList<String>();
                    for (var m : counts.keySet()) excerpt.addAll(Cluster.grep(logs, m, 3));
                    if (!excerpt.isEmpty()) report.attach("iteration " + i + " " + s, excerpt);
                    outcome.put(s, Map.of("ready", up, "state", cluster.state(s),
                            "waitedSeconds", Duration.between(t0, Instant.now()).toSeconds(), "markers", counts));
                    if (!up) {
                        allUp = false;
                        report.event("iteration " + i + ": " + s + " failed to start; restarting it");
                        cluster.restart(s);
                        cluster.waitReady(s, Duration.ofSeconds(360));
                    }
                }
                if (!allUp) failedIterations++;
                outcome.put("allStarted", allUp);
                perIteration.add(Map.of("iteration", i, "servers", outcome));
                report.event("iteration " + i + ": allStarted=" + allUp);
            }
            report.metric("iterations", perIteration);
            report.observe("all three servers start, every time",
                    "3/3 servers healthy on every iteration, no DDL race in the logs",
                    "occasional boot failures with pg_type_typname_nsp_index / tuple concurrently updated (G14)",
                    failedIterations + "/" + iterations + " iterations with a failed server; " + raceMarkers
                            + " schema-race log lines",
                    failedIterations == 0 && raceMarkers == 0, failedIterations > 0 || raceMarkers > 0,
                    "A clean run does not prove absence of the race; raise S10_ITERATIONS for evidence.");
        });
    }

    private void resetAppState() {
        report.event("reset app state: stop app JVMs, truncate consumer state and read models, start again");
        parallel(LabConfig.appServices(), s -> {
            try {
                cluster.stop(s);
            } catch (Exception e) {
                report.harnessError("stop " + s + ": " + e);
            }
        });
        for (var cell : LabConfig.CELLS) {
            try (var c = db.app(cell)) {
                truncate(c, cell, STATE_TABLES);
                truncate(c, cell, APP_TABLES);
            } catch (Exception e) {
                report.harnessError("app db " + cell + " reset: " + e);
            }
        }
        try (var c = db.consumers()) {
            truncate(c, "consumers", STATE_TABLES);
        } catch (Exception e) {
            report.harnessError("consumers db reset: " + e);
        }
        parallel(LabConfig.appServices(), s -> {
            try {
                cluster.start(s);
            } catch (Exception e) {
                report.harnessError("start " + s + ": " + e);
            }
        });
    }

    private void truncate(java.sql.Connection c, String db, List<String> tables) throws java.sql.SQLException {
        try (var st = c.createStatement()) {
            for (var t : tables) {
                try {
                    st.execute("TRUNCATE " + t);
                } catch (Exception e) {
                    report.note("truncate " + db + "." + t + ": " + e.getMessage());
                }
            }
        }
    }

    private static void parallel(List<String> services, java.util.function.Consumer<String> action) {
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var s : services) exec.submit(() -> action.accept(s));
        }
    }
}
