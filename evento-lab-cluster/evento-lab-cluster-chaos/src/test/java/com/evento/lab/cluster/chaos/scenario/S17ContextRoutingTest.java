package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.Json;
import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.RequestRecord;
import com.evento.lab.cluster.chaos.Rows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * S17: consumers separated per region by Evento contexts only.
 *
 * <p>There is one {@code OrderProjector} class; each order-query deployment
 * passes its {@code EVENTO_CONTEXTS} to {@code setComponentContexts}, and the
 * bundle starts one consumer engine per context, each fetching only that
 * context. Checked on the running stack:
 * <ul>
 *   <li>one JVM ({@code order-query-eu}, {@code EUROPA,AFRICA}) runs two engines;</li>
 *   <li>consumer ids are {@code <bundleId>_<Component>_<version>_<context>}, one per
 *       engine, registered by the instance that runs it and checkpointed in that
 *       deployment's cell database (the ALL lane in {@code consumers});</li>
 *   <li>no cross-context rows: each order lands in its own cell's {@code order_view},
 *       written by the deployment that consumes its region, and nowhere else;</li>
 *   <li>the wildcard deployment ({@code *}) sees every region.</li>
 * </ul>
 *
 * <p>Knobs: {@code S17_ORDERS} (orders per region, 2), {@code S17_CONVERGENCE_S} (90).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S17ContextRoutingTest extends AbstractScenario {

    /** Consumer ids each deployment must run (and register under its instance id). */
    static Map<String, Set<String>> expectedEngines() {
        var out = new LinkedHashMap<String, Set<String>>();
        for (var r : LabConfig.REGIONS) {
            out.computeIfAbsent(LabConfig.queryService(r), k -> new TreeSet<>()).add("order-query_OrderProjector_1_" + r);
        }
        for (var s : LabConfig.GLOBAL_LANE_SERVICES) {
            out.put(s, new TreeSet<>(Set.of("order-query_CatalogProjector_1_" + LabConfig.GLOBAL_CONTEXT)));
        }
        out.put(LabConfig.WILDCARD_QUERY_SERVICE, new TreeSet<>(Set.of("order-query_OrderProjector_1_*")));
        for (var r : LabConfig.REGIONS) {
            out.computeIfAbsent("order-saga-" + LabConfig.CELL_OF.get(r).toLowerCase(), k -> new TreeSet<>())
                    .add("order-saga_PaymentSaga_1_" + r);
        }
        out.put(LabConfig.OBSERVER_SERVICE, new TreeSet<>(Set.of("order-observer_NotificationObserver_1_*")));
        return out;
    }

    @Override
    protected String id() {
        return "S17";
    }

    @Override
    protected String title() {
        return "Context routing: one component class, one engine per configured context";
    }

    @Override
    protected List<String> gaps() {
        return List.of("setComponentContexts", "G15", "G16", "G8 (registration)");
    }

    @Test
    void contextRouting() throws Exception {
        scenario(() -> {
            // ---- the deployment plan, as the JVMs report it ---------------------------------------
            var eu = LabConfig.queryService("EUROPA");
            var who = cluster.httpGetInside(eu, "/whoami");
            report.param(eu + " whoami", who == null ? "unreadable" : Json.read(who));
            var plan = who == null ? null : Json.read(who).path("consumerPlan");
            var orderEngines = new ArrayList<String>();
            if (plan != null) plan.path("OrderProjector").forEach(n -> orderEngines.add(n.asText()));
            report.observe("one JVM, two contexts, two engines",
                    eu + " (EVENTO_CONTEXTS=EUROPA,AFRICA) plans OrderProjector engines [EUROPA, AFRICA] and no "
                            + "CatalogProjector engine",
                    plan == null ? "whoami not readable" : "plan " + plan,
                    plan == null ? null : orderEngines.equals(List.of("EUROPA", "AFRICA"))
                            && plan.path("CatalogProjector").isEmpty());

            // ---- registration: consumer ids per instance -------------------------------------------
            var registered = new TreeMap<String, Set<String>>();
            try (var c = db.es(); var st = c.createStatement();
                 var rs = st.executeQuery("SELECT instance_id, consumer_id FROM core__consumer")) {
                while (rs.next()) {
                    registered.computeIfAbsent(rs.getString(1), k -> new TreeSet<>()).add(rs.getString(2));
                }
            }
            var expected = expectedEngines();
            var mismatches = new ArrayList<String>();
            expected.forEach((instance, ids) -> {
                var got = registered.getOrDefault(instance, Set.of());
                if (!got.equals(ids)) mismatches.add(instance + ": expected " + ids + ", registered " + got);
            });
            report.attach("core__consumer by instance", registered.entrySet().stream()
                    .map(e -> e.getKey() + " -> " + e.getValue()).toList());
            report.observe("consumer ids per context, per instance",
                    "every deployment registers exactly <bundleId>_<Component>_1_<ctx> for each configured context",
                    "rows missing where the discovery race lost the registration (G8)",
                    mismatches.isEmpty() ? "all " + expected.size() + " deployments match" : String.join("; ", mismatches),
                    mismatches.isEmpty(), !mismatches.isEmpty(), null);

            // ---- checkpoints live where the deployment's consumer state is --------------------------
            var states = db.consumerStatesBestEffort();
            var misplaced = new ArrayList<String>();
            for (var s : states) {
                var expectedDb = expectedStateDb(s.consumerId());
                if (expectedDb != null && !expectedDb.equals(s.db())) misplaced.add(s.key() + " (expected in " + expectedDb + ")");
            }
            report.attach("consumer checkpoints", states.stream().map(s -> s.key() + "=" + s.lastSequence()).toList());
            report.observe("each lane's state in its deployment's database",
                    "regional engines in their cell database, the ALL lane in 'consumers', wildcard lanes in EU",
                    misplaced.isEmpty() ? states.size() + " checkpoints, all in place" : "misplaced " + misplaced,
                    misplaced.isEmpty());

            // ---- traffic: every region, then where did it land ------------------------------------
            var perRegion = LabConfig.envInt("S17_ORDERS", 2);
            var records = new ArrayList<RequestRecord>();
            var ids = new LinkedHashMap<String, String>();
            for (var region : LabConfig.REGIONS) {
                for (int i = 0; i < perRegion; i++) {
                    var id = gateway.newOrderId(prefix, region);
                    records.add(gateway.create(region, id, UUID.randomUUID().toString()));
                    ids.put(id, region);
                }
            }
            var results = checkInvariants(records, envSeconds("S17_CONVERGENCE_S", 90));
            var snap = db.snapshot(prefix);
            var wrongPlace = new ArrayList<String>();
            var wrongWriter = new ArrayList<String>();
            if (snap.cellViews() != null) {
                snap.cellViews().forEach((cell, rows) -> {
                    for (var row : rows) {
                        var region = ids.get(row.orderId());
                        if (region == null) continue;
                        if (!LabConfig.CELL_OF.get(region).equals(cell) || !region.equals(row.context())) {
                            wrongPlace.add(row.orderId() + " (" + region + ") in " + cell + " with context " + row.context());
                        }
                    }
                });
                for (var cell : LabConfig.CELLS) {
                    try (var c = db.app(cell); var ps = c.prepareStatement(
                            "SELECT order_id, updated_by FROM order_view WHERE order_id LIKE ?")) {
                        ps.setString(1, prefix.replace("_", "\\_") + "%");
                        try (var rs = ps.executeQuery()) {
                            while (rs.next()) {
                                var region = ids.get(rs.getString(1));
                                if (region != null && !LabConfig.queryService(region).equals(rs.getString(2))) {
                                    wrongWriter.add(rs.getString(1) + " written by " + rs.getString(2));
                                }
                            }
                        }
                    }
                }
            }
            report.observe("no cross-context rows",
                    "each order only in its region's cell database, with its region as context",
                    wrongPlace.isEmpty() ? ids.size() + " orders, all in place" : head(wrongPlace),
                    snap.cellViews() == null ? null : wrongPlace.isEmpty());
            report.observe("each region projected by its own deployment",
                    "order_view.updated_by = the order-query deployment whose EVENTO_CONTEXTS lists the region",
                    wrongWriter.isEmpty() ? "all rows written by the owning deployment" : head(wrongWriter),
                    snap.cellViews() == null ? null : wrongWriter.isEmpty());
            var seenByWildcard = snap.global() == null ? -1
                    : snap.global().stream().filter(v -> ids.containsKey(v.orderId())).count();
            var byContext = new TreeMap<String, Long>();
            if (snap.global() != null) {
                snap.global().stream().filter(v -> ids.containsKey(v.orderId()))
                        .forEach(v -> byContext.merge(v.context(), 1L, Long::sum));
            }
            report.observe("the wildcard deployment sees every region",
                    "order_view_global holds all " + ids.size() + " orders, " + perRegion + " per region",
                    seenByWildcard + " orders, by context " + byContext,
                    seenByWildcard == ids.size() && byContext.size() == LabConfig.REGIONS.size());
            var i4 = find(results, "I4");
            report.observe("I4 regional isolation", i4.expected(), i4.observed(), i4.passed());
            var globalRows = new ArrayList<Rows.ViewRow>();
            if (snap.cellViews() != null) snap.cellViews().values().forEach(globalRows::addAll);
            report.observe("ALL events never reach an order read model",
                    "no order_view row with context ALL",
                    globalRows.stream().filter(v -> LabConfig.GLOBAL_CONTEXT.equals(v.context())).count() + " such rows",
                    globalRows.stream().noneMatch(v -> LabConfig.GLOBAL_CONTEXT.equals(v.context())));
            Cluster.sleep(Duration.ofSeconds(1));
        });
    }

    /** The context of a consumer id {@code <bundleId>_<Component>_1_<context>} (contexts may contain '_'). */
    static String contextOf(String consumerId) {
        var i = consumerId.indexOf("_1_");
        return i < 0 ? null : consumerId.substring(i + 3);
    }

    /** Where a consumer's checkpoint must live, from its id; null = not a lab lane. */
    static String expectedStateDb(String consumerId) {
        var context = contextOf(consumerId);
        if (context == null) return null;
        if (consumerId.startsWith("order-query_CatalogProjector_")) return "consumers";
        if (context.equals("*")) return LabConfig.GLOBAL_VIEW_CELL;
        var cell = LabConfig.CELL_OF.get(context);
        if (cell != null && (consumerId.startsWith("order-query_") || consumerId.startsWith("order-saga_")
                || consumerId.startsWith("order-observer"))) {
            return cell;
        }
        return null;
    }

    private static String head(List<String> l) {
        return l.size() <= 5 ? l.toString() : l.subList(0, 5) + " ... (" + l.size() + ")";
    }
}
