package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.Db;
import com.evento.lab.cluster.chaos.Gateway;
import com.evento.lab.cluster.chaos.HarnessException;
import com.evento.lab.cluster.chaos.InvariantResult;
import com.evento.lab.cluster.chaos.Invariants;
import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.RequestRecord;
import com.evento.lab.cluster.chaos.ResultWriter;
import com.evento.lab.cluster.chaos.ScenarioReport;
import com.evento.lab.cluster.chaos.Toxi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Shared scaffolding of every chaos scenario.
 *
 * <p>Contract: a scenario records what it observes into {@link #report} and
 * never asserts on the system under test. The only hard failure is a harness
 * error (docker unreachable, stack not up, ...). {@code @AfterEach} always
 * undoes the fault (registered cleanups, toxics, paused/stopped containers),
 * writes {@code results/<scenario>/<label>/} and waits for the stack to be
 * ready again, so scenarios run in any order.
 */
abstract class AbstractScenario {

    /** Log markers of the readiness assessment, counted in every scenario that looks at logs. */
    static final List<String> MARKERS = List.of(
            "no handler for", "no available handler for", "no connection for", "peer disconnected:",
            "event=handshake_complete", "event=bundle_state_transition", "event=supervisor_session_failed",
            "event=bundle_correlation_expired", "event=server_instance_mismatch", "event=lock_release_failed",
            "Skipping unregister of bundle", "[BundleService] Unregister called for bundleId=",
            "duplicate key", "core__bundle_pkey", "core__component_pkey", "core__payload_pkey",
            "pg_type_typname_nsp_index", "tuple concurrently updated", "RequestTimeoutException",
            "event=gateway_command_failed", "event=lab_ready", "deadlock detected",
            "Failed to acquire advisory lock");

    /**
     * Default background load (orders/min/region) of the fault scenarios S3, S7, S8, S9.
     * The assessment's 20/min is about twice what the pre-G9 cluster can append (see S00
     * and FINDINGS.md, G9): under that overload APAC acknowledges only ~40% of commands
     * without any fault, which would drown the effect of the injected fault. 4/min keeps
     * the es-lock at roughly half utilisation. Override per scenario with {@code Sxx_RATE}
     * or globally with {@code LAB_FAULT_RATE}.
     */
    static final double FAULT_RATE = env("LAB_FAULT_RATE", 4);

    protected LabConfig config;
    protected Cluster cluster;
    protected Toxi toxi;
    protected Gateway gateway;
    protected Db db;
    protected ScenarioReport report;
    /** Every order id of this run starts with it: {@code s03-before-3f9a2c-}. */
    protected String prefix;

    private final Deque<Runnable> cleanups = new ArrayDeque<>();
    private final Map<String, Map<String, Integer>> allMarkers = new LinkedHashMap<>();
    private Instant scenarioStart;
    private boolean stackReachable;

    protected abstract String id();

    protected abstract String title();

    protected abstract List<String> gaps();

    /** Services this scenario must find ready before it starts (default: the whole default topology). */
    protected List<String> requiredServices() {
        var out = new ArrayList<String>(LabConfig.servers());
        out.addAll(LabConfig.appServices());
        out.add("lb");
        return out;
    }

    @BeforeEach
    void setUpScenario() {
        config = LabConfig.fromEnvironment();
        cluster = new Cluster(config);
        toxi = new Toxi(config.toxiproxyUrl());
        gateway = new Gateway(config.lbUrl());
        db = new Db(config);
        report = new ScenarioReport(id(), title(), gaps(), config.label());
        prefix = (id() + "-" + config.label() + "-" + UUID.randomUUID().toString().substring(0, 6) + "-")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
        report.param("orderIdPrefix", prefix);
        report.param("composeFile", config.composeFile().toString());
        scenarioStart = Instant.now();

        var docker = cluster.docker(Duration.ofSeconds(30), "version", "--format", "{{.Server.Version}}");
        if (!docker.ok()) {
            throw harness("docker is not reachable: " + docker.combined());
        }
        if (!cluster.composeFileExists()) {
            throw harness("compose file not found: " + config.composeFile());
        }
        if (!toxi.reachable()) {
            throw harness("toxiproxy API not reachable at " + config.toxiproxyUrl()
                    + " - is the stack up (scripts/up.sh)?");
        }
        stackReachable = true;
        toxi.sweepLeftovers();
        for (var s : requiredServices()) cluster.ensureRunning(s);
        var ready = cluster.waitAllReady(requiredServices(), Duration.ofSeconds(
                LabConfig.envInt("EVENTO_LAB_READY_TIMEOUT_S", 420)));
        var notReady = ready.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).toList();
        report.param("servicesNotReadyAtStart", notReady);
        if (!notReady.isEmpty()) {
            report.note("services not ready at start (scenario ran anyway): " + notReady);
        }
        report.event("scenario start");
    }

    @AfterEach
    void tearDownScenario() {
        if (report == null) return;
        if (!stackReachable) {
            report.finish();
            new ResultWriter(config.resultsDir()).write(report);
            return;
        }
        report.event("cleanup start");
        while (!cleanups.isEmpty()) {
            var c = cleanups.pop();
            try {
                c.run();
            } catch (Exception e) {
                report.harnessError("cleanup step failed: " + e);
            }
        }
        try {
            var errs = toxi.heal();
            if (!errs.isEmpty()) report.harnessError("toxiproxy heal: " + errs);
        } catch (Exception e) {
            report.harnessError("toxiproxy heal failed: " + e);
        }
        for (var s : requiredServices()) {
            try {
                cluster.ensureRunning(s);
            } catch (Exception e) {
                report.harnessError("restore " + s + ": " + e);
            }
        }
        report.attach("stack actions", merge(cluster.actions(), toxi.actions()));
        report.event("cleanup done");
        report.finish();
        new ResultWriter(config.resultsDir()).write(report);
        // leave the stack ready for the next scenario (not part of the result)
        cluster.waitAllReady(requiredServices(), Duration.ofSeconds(LabConfig.envInt("EVENTO_LAB_READY_TIMEOUT_S", 420)));
    }

    /** Run the scenario body; anything thrown is a harness error (recorded, then rethrown to fail the test). */
    protected void scenario(ThrowingRunnable body) throws Exception {
        try {
            body.run();
        } catch (Throwable t) {
            report.harnessError(t.toString());
            if (t instanceof Exception e) throw e;
            throw new HarnessException("scenario aborted", t);
        }
    }

    @FunctionalInterface
    protected interface ThrowingRunnable {
        void run() throws Exception;
    }

    private HarnessException harness(String message) {
        report.harnessError(message);
        return new HarnessException(message);
    }

    /** Register an undo step; run LIFO in {@code @AfterEach}, even if the scenario aborted. */
    protected void onCleanup(Runnable r) {
        cleanups.push(r);
    }

    protected Instant scenarioStart() {
        return scenarioStart;
    }

    // ---- common steps -----------------------------------------------------------------------------

    protected List<RequestRecord> load(List<String> regions, double ratePerMinute, int items, double completeRatio,
                                       Duration duration, java.util.function.Consumer<Duration> onTick) {
        var plan = new Gateway.LoadPlan(regions, ratePerMinute, items, completeRatio, duration);
        report.param("load", Map.of("regions", regions, "ratePerMinutePerRegion", ratePerMinute,
                "itemsPerOrder", items, "completeRatio", completeRatio, "durationSeconds", duration.toSeconds()));
        report.event("load start " + regions + " @" + ratePerMinute + "/min for " + duration.toSeconds() + " s");
        var recs = gateway.runLoad(prefix, plan, onTick);
        report.event("load done: " + recs.size() + " requests");
        return recs;
    }

    protected List<InvariantResult> checkInvariants(Collection<RequestRecord> records, Duration convergence) {
        report.metric("failedRequestsByError", errorsByMessage(records));
        report.event("invariants: converging up to " + convergence.toSeconds() + " s");
        var results = Invariants.checkAll(() -> db.snapshot(prefix), records, convergence);
        report.invariants(results);
        // Lag (ES head - checkpoint) per consumer right after the convergence wait: tells a
        // slow lane (lag shrinking, N4) from a stuck one when I6 fails.
        try {
            var head = db.esHead();
            var lag = new java.util.TreeMap<String, Long>();
            db.checkpoints().forEach((k, v) -> lag.put(k, head - v));
            report.metric("consumerLagAfterConvergenceWait", Map.of("esHead", head, "lag", lag));
        } catch (Exception e) {
            report.note("consumer lag not readable: " + e.getMessage());
        }
        return results;
    }

    protected static InvariantResult find(List<InvariantResult> results, String id) {
        return results.stream().filter(r -> r.id().equals(id)).findFirst()
                .orElse(InvariantResult.skipped(id, id, "not evaluated"));
    }

    /** Marker counts per service since {@code since}; also attaches the first matching lines. */
    protected Map<String, Map<String, Integer>> markers(List<String> services, Instant since, List<String> markers) {
        var out = new LinkedHashMap<String, Map<String, Integer>>();
        var all = allMarkers;
        for (var s : services) {
            var logs = cluster.logsSince(s, since);
            var counts = Cluster.countMarkers(logs, markers);
            counts.values().removeIf(v -> v == 0);
            out.put(s, counts);
            var excerpt = new ArrayList<String>();
            for (var m : counts.keySet()) excerpt.addAll(Cluster.grep(logs, m, 3));
            if (!excerpt.isEmpty()) report.attach("log excerpts: " + s, excerpt);
        }
        // several calls in one scenario (servers, then app JVMs) must not overwrite each other
        all.putAll(out);
        report.metric("logMarkers", new LinkedHashMap<>(all));
        return out;
    }

    protected static int total(Map<String, Map<String, Integer>> markers, String marker) {
        return markers.values().stream().mapToInt(m -> m.getOrDefault(marker, 0)).sum();
    }

    protected static List<RequestRecord> filter(Collection<RequestRecord> rs, Predicate<RequestRecord> p) {
        return rs.stream().filter(p).toList();
    }

    protected static List<RequestRecord> commandsIn(Collection<RequestRecord> rs, Collection<String> regions) {
        return filter(rs, r -> r.isCommand() && regions.contains(r.region()));
    }

    /** First acknowledged command of {@code regions} sent at or after {@code t}, or null. */
    protected static RequestRecord firstAckAfter(Collection<RequestRecord> rs, Collection<String> regions, Instant t) {
        return rs.stream()
                .filter(r -> r.isCommand() && regions.contains(r.region())
                        && r.outcome() == RequestRecord.Outcome.ACK && r.startMs() >= t.toEpochMilli())
                .min((a, b) -> Long.compare(a.startMs(), b.startMs()))
                .orElse(null);
    }

    /** Commands in flight at instant {@code t}. */
    protected static List<RequestRecord> inFlightAt(Collection<RequestRecord> rs, Instant t) {
        long ms = t.toEpochMilli();
        return filter(rs, r -> r.isCommand() && r.startMs() <= ms && r.startMs() + r.latencyMs() >= ms);
    }

    protected static double successRate(Collection<RequestRecord> rs) {
        var cmds = filter(rs, RequestRecord::isCommand);
        if (cmds.isEmpty()) return Double.NaN;
        return cmds.stream().filter(r -> r.outcome() == RequestRecord.Outcome.ACK).count() / (double) cmds.size();
    }

    protected static Map<String, Long> kinds(Collection<RequestRecord> rs) {
        var out = new java.util.TreeMap<String, Long>();
        for (var r : rs) if (r.outcome() != RequestRecord.Outcome.ACK) out.merge(r.kind(), 1L, Long::sum);
        return out;
    }

    /**
     * Failed requests grouped by "kind: error message" (order ids and keys masked), so a
     * report says *why* commands failed, not only how many (e.g. a Postgres "deadlock
     * detected" behind kind=error).
     */
    protected static Map<String, Long> errorsByMessage(Collection<RequestRecord> rs) {
        var out = new java.util.TreeMap<String, Long>();
        for (var r : rs) {
            if (r.outcome() == RequestRecord.Outcome.ACK) continue;
            var msg = r.error() == null ? "" : r.error()
                    .replaceAll("s\\d\\d[a-z0-9-]*", "<id>")
                    .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<uuid>")
                    .replaceAll("after [0-9]+ms", "after <n>ms");
            if (msg.length() > 200) msg = msg.substring(0, 200) + "...";
            out.merge(r.kind() + ": " + msg, 1L, Long::sum);
        }
        return out;
    }

    protected static double env(String name, double def) {
        var v = System.getenv(name);
        return v == null || v.isBlank() ? def : Double.parseDouble(v.trim());
    }

    protected static Duration envSeconds(String name, int def) {
        return Duration.ofSeconds(LabConfig.envInt(name, def));
    }

    /** Wait until a region answers a command again; returns the wait, or null on timeout. */
    protected Duration waitCommandsWork(String region, Duration timeout) {
        var start = Instant.now();
        var ok = Cluster.waitUntil(timeout, Duration.ofSeconds(3), () -> {
            var r = gateway.create(region, gateway.newOrderId(prefix + "probe-", region), UUID.randomUUID().toString());
            return r.outcome() == RequestRecord.Outcome.ACK;
        });
        return ok ? Duration.between(start, Instant.now()) : null;
    }

    private static List<String> merge(List<String> a, List<String> b) {
        var out = new ArrayList<String>(a);
        out.addAll(b);
        out.sort(String::compareTo);
        return out;
    }
}
