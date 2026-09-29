package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.RequestRecord;
import com.evento.lab.cluster.chaos.Rows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * S16: cross-region events (literal context {@code ALL}) and the single global lane.
 *
 * <p>A price change is one {@code PriceChangedEvent} with context {@code ALL}. No
 * regional consumer receives it; the one lane {@code order-query_CatalogProjector_1_ALL}
 * (two replicas, {@code order-query-global-eu} and {@code -apac}, competing through the
 * shared {@code consumers} database) writes it into all three regional databases,
 * idempotently (a target applies it only if its sequence is above the stored one).
 *
 * <ol>
 *   <li><b>Fan-out:</b> every ALL event reaches app-pg-eu, -am and -apac exactly once.</li>
 *   <li><b>One lane:</b> one checkpoint for the ALL lane cluster-wide, and every event
 *       fanned out by exactly one replica.</li>
 *   <li><b>Target outage:</b> {@code docker stop app-pg-apac} while ALL events keep
 *       coming. The lane serves the healthy targets for the head event, then stalls
 *       for <em>every</em> region until APAC is back (the known trade-off of one lane
 *       for all targets). After healing: convergence, no duplicates.</li>
 *   <li><b>Takeover:</b> kill the replica holding the lane; the standby continues.</li>
 * </ol>
 *
 * <p>Knobs: {@code S16_EVENTS} (5), {@code S16_OUTAGE_EVENTS} (4), {@code S16_OUTAGE_S} (40),
 * {@code S16_CONVERGENCE_S} (120).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S16GlobalFanOutTest extends AbstractScenario {

    private static final String LANE = "order-query_CatalogProjector_1_" + LabConfig.GLOBAL_CONTEXT;
    private static final String LANE_KEY = "consumers/" + LANE;
    private static final Pattern FANOUT_SEQ = Pattern.compile("event=lab_fanout seq=(\\d+) sku=(\\S+)");

    @Override
    protected String id() {
        return "S16";
    }

    @Override
    protected String title() {
        return "Global ALL events: single lane, fan-out to every regional database";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G24 (one lane per bundleId+context)", "G11", "N4");
    }

    @Test
    void globalFanOut() throws Exception {
        scenario(() -> {
            var since = Instant.now();
            var skuPrefix = prefix + "sku-";
            var convergence = envSeconds("S16_CONVERGENCE_S", 120);

            // ---- 1. fan-out --------------------------------------------------------------------
            var first = sendPrices(skuPrefix + "a", LabConfig.envInt("S16_EVENTS", 5));
            var reached = waitFanOut(first, LabConfig.CELLS, convergence);
            var snapshot = prices(skuPrefix + "a");
            report.metric("fanOutSeconds", reached == null ? null : reached.toMillis() / 1000.0);
            report.attach("catalog_price after the fan-out", describe(snapshot));
            var wrong = exactlyOnceViolations(first, snapshot, LabConfig.CELLS);
            report.observe("ALL event reaches every regional database exactly once",
                    "each price in app-pg-eu, -am and -apac with its event's sequence, applied_events = 1, context ALL",
                    wrong.isEmpty() ? first.size() + " events x 3 databases, each applied once"
                            : wrong.size() + " problem(s): " + head(wrong),
                    reached != null && wrong.isEmpty());
            var writers = new TreeMap<String, Integer>();
            snapshot.forEach(r -> writers.merge(String.valueOf(r.updatedBy()), 1, Integer::sum));
            report.metric("fanOutWriters", writers);
            report.observe("only the global lane writes the catalog",
                    "every catalog_price row written by an order-query-global-* replica",
                    "writers " + writers,
                    writers.keySet().stream().allMatch(w -> LabConfig.GLOBAL_LANE_SERVICES.contains(w)));

            // ---- 2. one lane ---------------------------------------------------------------------
            var states = db.consumerStatesBestEffort();
            var laneRows = states.stream().filter(s -> s.consumerId().contains("CatalogProjector")).toList();
            report.metric("catalogConsumerStates", laneRows.stream().map(Rows.ConsumerStateRow::key).toList());
            report.observe("one ALL lane cluster-wide",
                    "exactly one CatalogProjector checkpoint, " + LANE_KEY + " (the replicas share it)",
                    laneRows.stream().map(r -> r.key() + "=" + r.lastSequence()).toList().toString(),
                    laneRows.size() == 1 && laneRows.getFirst().key().equals(LANE_KEY));
            var byReplica = fanOutsByReplica(since);
            report.metric("fanOutEventsByReplica", byReplica);
            var twice = processedTwice(byReplica);
            report.observe("each ALL event fanned out by one replica",
                    "every sequence appears in exactly one replica's event=lab_fanout log (standby idle meanwhile)",
                    twice.isEmpty() ? "no sequence processed by both replicas" : "processed by both: " + twice,
                    twice.isEmpty());
            report.note("The lane is one (one consumer id, one lock row); which replica holds it can change between "
                    + "fetch cycles (N4: the JDBC consumer lock is taken per cycle, not sticky).");

            // ---- 3. target outage ------------------------------------------------------------------
            var seqBefore = checkpoint();
            var samples = Collections.synchronizedList(new ArrayList<String>());
            var stop = new AtomicBoolean(false);
            var sampler = Thread.ofVirtual().start(() -> {
                while (!stop.get()) {
                    samples.add(Instant.now() + " " + LANE_KEY + "=" + checkpoint());
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });
            onCleanup(() -> {
                cluster.ensureRunning("app-pg-apac");
                cluster.waitReady("app-pg-apac", Duration.ofSeconds(120));
            });
            cluster.stop("app-pg-apac");
            report.event("stopped app-pg-apac (APAC's regional database)");
            var outage = sendPrices(skuPrefix + "b", LabConfig.envInt("S16_OUTAGE_EVENTS", 4));
            Cluster.sleep(envSeconds("S16_OUTAGE_S", 40));
            var during = prices(skuPrefix + "b");
            var cpDuring = checkpoint();
            var healthyGot = new TreeMap<String, Long>();
            for (var cell : List.of("EU", "AM")) {
                healthyGot.put(cell, during.stream().filter(r -> r.cell().equals(cell)).count());
            }
            report.metric("outageEvents", outage.size());
            report.metric("outageRowsInHealthyCells", healthyGot);
            report.metric("laneCheckpointBeforeOutage", seqBefore);
            report.metric("laneCheckpointDuringOutage", cpDuring);
            var maxHealthy = healthyGot.values().stream().mapToLong(Long::longValue).max().orElse(0);
            var stalled = maxHealthy < outage.size();
            report.observe("healthy regions during APAC's database outage",
                    "the single ALL lane stalls for every region: EU and AM get at most the head event, the "
                            + "checkpoint stays before it (known trade-off of one lane with N targets)",
                    "EU/AM received " + healthyGot + " of " + outage.size() + " ALL events; lane checkpoint "
                            + seqBefore + " -> " + cpDuring,
                    stalled);
            report.note("A per-target lane (one consumer per regional database) would keep EU and AM current "
                    + "during an APAC outage, at the price of N lanes to operate; the lab keeps one lane on purpose.");

            cluster.start("app-pg-apac");
            var back = cluster.waitReady("app-pg-apac", Duration.ofSeconds(120));
            var healedAt = Instant.now();
            report.event("app-pg-apac back, ready=" + back);
            var all = new LinkedHashMap<String, Long>(first);
            all.putAll(outage);
            var converged = waitFanOut(all, LabConfig.CELLS, convergence);
            stop.set(true);
            sampler.interrupt();
            report.attach("ALL lane checkpoint (every 3 s)", List.copyOf(samples));
            var after = prices(skuPrefix);
            var dup = exactlyOnceViolations(all, after, LabConfig.CELLS);
            report.metric("secondsToConvergeAfterHeal", converged == null ? null : converged.toMillis() / 1000.0);
            report.param("healedAt", healedAt.toString());
            report.metric("replaysAbsorbed", after.stream().mapToInt(Rows.PriceRow::staleSkips).sum());
            report.observe("convergence after healing, no duplicates",
                    "every ALL event in all three databases, applied_events = 1 (replays refused by the sequence guard)",
                    converged == null ? "not converged within " + convergence.toSeconds() + " s: " + head(dup)
                            : "converged; " + (dup.isEmpty() ? "no duplicate" : head(dup)),
                    converged != null && dup.isEmpty());

            // ---- 4. takeover -------------------------------------------------------------------------
            var holder = latestHolder(since);
            report.param("laneHolderBeforeKill", holder);
            if (holder == null) {
                report.observe("standby takes over the ALL lane", "the other replica continues", "no holder found in logs",
                        null);
                return;
            }
            var standby = LabConfig.GLOBAL_LANE_SERVICES.stream().filter(s -> !s.equals(holder)).findFirst().orElseThrow();
            onCleanup(() -> {
                cluster.ensureRunning(holder);
                cluster.waitReady(holder, Duration.ofSeconds(300));
            });
            var killAt = Instant.now();
            cluster.kill(holder);
            report.event("killed the lane holder " + holder);
            var takeover = sendPrices(skuPrefix + "c", 3);
            var took = waitFanOut(takeover, LabConfig.CELLS, convergence);
            var afterKill = prices(skuPrefix + "c");
            var writersAfter = new TreeMap<String, Integer>();
            afterKill.forEach(r -> writersAfter.merge(String.valueOf(r.updatedBy()), 1, Integer::sum));
            report.metric("takeoverSeconds", took == null ? null : Duration.between(killAt, Instant.now()).toMillis() / 1000.0);
            report.observe("standby takes over the ALL lane",
                    "after the holder dies, " + standby + " fans out the next events to all three databases",
                    took == null ? "not fanned out within " + convergence.toSeconds() + " s" : "written by " + writersAfter,
                    took != null && writersAfter.keySet().equals(java.util.Set.of(standby)));

            markers(new ArrayList<>(LabConfig.GLOBAL_LANE_SERVICES), since,
                    List.of("event=lab_fanout", "FanOutIncompleteException", "Event processing failed",
                            "Connection refused", "event=lock_release_failed"));
        });
    }

    /** Send {@code n} price changes with fresh SKUs through random regions; returns sku -> event sequence. */
    private Map<String, Long> sendPrices(String skuPrefix, int n) throws SQLException {
        var sent = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            var region = LabConfig.REGIONS.get(i % LabConfig.REGIONS.size());
            var sku = skuPrefix + i;
            var r = gateway.setPrice(region, sku, 1000L + i, UUID.randomUUID().toString());
            if (r.outcome() == RequestRecord.Outcome.ACK) {
                sent.add(sku);
            } else {
                report.note("price change " + sku + " via " + region + " not acknowledged: " + r.kind() + " " + r.error());
            }
        }
        report.event("sent " + sent.size() + "/" + n + " ALL events (" + skuPrefix + "*)");
        var seqs = new LinkedHashMap<String, Long>();
        try (var c = db.es(); var ps = c.prepareStatement(
                "SELECT max(event_sequence_number), max(context) FROM es__events WHERE aggregate_id = ?")) {
            for (var sku : sent) {
                ps.setString(1, "price-" + sku);
                try (var rs = ps.executeQuery()) {
                    if (rs.next() && rs.getObject(1) != null) {
                        seqs.put(sku, rs.getLong(1));
                        if (!LabConfig.GLOBAL_CONTEXT.equals(rs.getString(2))) {
                            report.note(sku + " stored with context " + rs.getString(2) + ", not ALL");
                        }
                    }
                }
            }
        }
        return seqs;
    }

    /** Wait until every sku is in every cell database with (at least) its sequence; the wait, or null. */
    private Duration waitFanOut(Map<String, Long> expected, List<String> cells, Duration timeout) {
        var start = Instant.now();
        var ok = Cluster.waitUntil(timeout, Duration.ofSeconds(2), () -> {
            for (var cell : cells) {
                List<Rows.PriceRow> rows;
                try {
                    rows = db.prices(cell, prefix);
                } catch (SQLException e) {
                    return false;
                }
                for (var e : expected.entrySet()) {
                    if (rows.stream().noneMatch(r -> r.sku().equals(e.getKey()) && r.lastSeq() >= e.getValue())) {
                        return false;
                    }
                }
            }
            return true;
        });
        return ok ? Duration.between(start, Instant.now()) : null;
    }

    private List<Rows.PriceRow> prices(String skuPrefix) {
        var out = new ArrayList<Rows.PriceRow>();
        for (var cell : LabConfig.CELLS) {
            try {
                out.addAll(db.prices(cell, skuPrefix));
            } catch (SQLException e) {
                report.note("catalog_price in " + cell + " not readable: " + e.getMessage());
            }
        }
        return out;
    }

    private static List<String> exactlyOnceViolations(Map<String, Long> expected, List<Rows.PriceRow> rows,
                                                      List<String> cells) {
        var out = new ArrayList<String>();
        for (var e : expected.entrySet()) {
            for (var cell : cells) {
                var row = rows.stream().filter(r -> r.cell().equals(cell) && r.sku().equals(e.getKey())).findFirst();
                if (row.isEmpty()) {
                    out.add(e.getKey() + " missing in " + cell);
                } else if (row.get().lastSeq() != e.getValue() || row.get().appliedEvents() != 1
                        || !LabConfig.GLOBAL_CONTEXT.equals(row.get().context())) {
                    out.add(e.getKey() + " in " + cell + ": seq " + row.get().lastSeq() + "/" + e.getValue()
                            + ", applied " + row.get().appliedEvents() + ", context " + row.get().context());
                }
            }
        }
        return out;
    }

    private long checkpoint() {
        return db.checkpointsBestEffort().getOrDefault(LANE_KEY, -1L);
    }

    /** replica -> sequences it fanned out (from its event=lab_fanout log lines). */
    private Map<String, List<Long>> fanOutsByReplica(Instant since) {
        var out = new TreeMap<String, List<Long>>();
        for (var s : LabConfig.GLOBAL_LANE_SERVICES) {
            var seqs = new ArrayList<Long>();
            var m = FANOUT_SEQ.matcher(cluster.logsSince(s, since));
            while (m.find()) {
                if (m.group(2).startsWith(prefix)) seqs.add(Long.parseLong(m.group(1)));
            }
            out.put(s, seqs);
        }
        return out;
    }

    /** Sequences that both replicas fanned out (a retry inside one replica is not counted). */
    static List<Long> processedTwice(Map<String, List<Long>> byReplica) {
        var seen = new TreeMap<Long, java.util.Set<String>>();
        byReplica.forEach((replica, seqs) -> seqs.forEach(q -> seen.computeIfAbsent(q, k -> new java.util.TreeSet<>())
                .add(replica)));
        return seen.entrySet().stream().filter(e -> e.getValue().size() > 1).map(Map.Entry::getKey).toList();
    }

    /** The replica with the most recent event=lab_fanout line. */
    private String latestHolder(Instant since) {
        String best = null;
        String bestLine = "";
        for (var s : LabConfig.GLOBAL_LANE_SERVICES) {
            var lines = Cluster.grep(cluster.logsSince(s, since), "event=lab_fanout", 100_000);
            if (!lines.isEmpty() && lines.getLast().compareTo(bestLine) > 0) {
                bestLine = lines.getLast();
                best = s;
            }
        }
        return best;
    }

    private static List<String> describe(List<Rows.PriceRow> rows) {
        return rows.stream().map(r -> r.cell() + " " + r.sku() + " seq=" + r.lastSeq() + " applied=" + r.appliedEvents()
                + " stale=" + r.staleSkips() + " ctx=" + r.context() + " by=" + r.updatedBy()).toList();
    }

    private static String head(List<String> l) {
        return l.size() <= 5 ? l.toString() : l.subList(0, 5) + " ... (" + l.size() + ")";
    }
}
