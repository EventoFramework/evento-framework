package com.evento.lab.cluster.chaos;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * I1..I8 of the readiness assessment as pure functions over rows (see
 * {@link Rows}) plus the client ledger. They never assert: each returns an
 * {@link InvariantResult} with counts and the first violations.
 *
 * <p>Event types are matched on the simple payload class name
 * ({@code OrderCreatedEvent}, ...). A payment is stored by the server under
 * {@code aggregate_id = "payment-" + orderId} (service-command lock id).
 */
public final class Invariants {

    public static final String CREATED = "OrderCreatedEvent";
    public static final String ITEM_ADDED = "OrderItemAddedEvent";
    public static final String COMPLETED = "OrderCompletedEvent";
    public static final String PAID = "PaymentProcessedEvent";
    public static final String PAYMENT_PREFIX = "payment-";

    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("idempotencyKey[\\\\\"\\s:]+([A-Za-z0-9#_.:\\-]{6,})");

    private Invariants() {
    }

    /** What the event store says an order is, as the projectors should end up seeing it. */
    public record ExpectedOrder(String orderId, String context, int created, int items, int completed,
                                int paid, long lastOrderSeq, long lastSeqWithPayment) {
        String status() {
            return completed > 0 ? "COMPLETED" : "CREATED";
        }
    }

    // ---- derived state --------------------------------------------------------------------------

    public static String orderIdOf(Rows.EsEvent e) {
        var id = e.aggregateId();
        if (id == null) return null;
        return e.is(PAID) && id.startsWith(PAYMENT_PREFIX) ? id.substring(PAYMENT_PREFIX.length()) : id;
    }

    public static Map<String, ExpectedOrder> expectedOrders(Collection<Rows.EsEvent> events) {
        var acc = new TreeMap<String, long[]>(); // created, items, completed, paid, lastOrderSeq, lastSeq
        var ctx = new HashMap<String, String>();
        for (var e : events) {
            var id = orderIdOf(e);
            if (id == null) continue;
            var a = acc.computeIfAbsent(id, k -> new long[6]);
            if (e.is(CREATED)) {
                a[0]++;
                ctx.putIfAbsent(id, e.context());
            } else if (e.is(ITEM_ADDED)) {
                a[1]++;
            } else if (e.is(COMPLETED)) {
                a[2]++;
            } else if (e.is(PAID)) {
                a[3]++;
            }
            if (!e.is(PAID)) {
                a[4] = Math.max(a[4], e.seq());
                ctx.putIfAbsent(id, e.context());
            }
            a[5] = Math.max(a[5], e.seq());
        }
        var out = new LinkedHashMap<String, ExpectedOrder>();
        acc.forEach((id, a) -> out.put(id, new ExpectedOrder(id, ctx.get(id), (int) a[0], (int) a[1], (int) a[2],
                (int) a[3], a[4], a[5])));
        return out;
    }

    // ---- I1 ---------------------------------------------------------------------------------------

    /** I1: every command acknowledged with a 2xx has its event in {@code es__events}. */
    public static InvariantResult i1NoLostEvents(Map<String, OrderLedger> ledgers, Collection<Rows.EsEvent> events) {
        var expected = expectedOrders(events);
        var violations = new ArrayList<String>();
        int ackedOps = 0;
        int lostOps = 0;
        int ambiguousStored = 0;
        for (var l : ledgers.values()) {
            ackedOps += l.createAcked + l.itemsAcked + l.completeAcked;
            var e = expected.get(l.orderId);
            int created = e == null ? 0 : e.created();
            int items = e == null ? 0 : e.items();
            int completed = e == null ? 0 : e.completed();
            if (created < Math.min(1, l.createAcked)) {
                lostOps += 1;
                violations.add(l.orderId + ": create acknowledged, no " + CREATED + " stored");
            }
            if (items < l.itemsAcked) {
                lostOps += l.itemsAcked - items;
                violations.add(l.orderId + ": " + l.itemsAcked + " item(s) acknowledged, " + items + " stored");
            }
            if (completed < Math.min(1, l.completeAcked)) {
                lostOps += 1;
                violations.add(l.orderId + ": complete acknowledged, no " + COMPLETED + " stored");
            }
            // stored although never acknowledged: legal (unknown outcome), but worth counting
            if (created > l.createAcked && l.createIndeterminate > 0) ambiguousStored++;
            if (items > l.itemsAcked && l.itemsIndeterminate > 0) ambiguousStored += items - l.itemsAcked;
            if (completed > l.completeAcked && l.completeIndeterminate > 0) ambiguousStored++;
        }
        var metrics = new LinkedHashMap<String, Object>();
        metrics.put("orders", ledgers.size());
        metrics.put("acknowledgedOps", ackedOps);
        metrics.put("lostOps", lostOps);
        metrics.put("storedWithUnknownOutcome", ambiguousStored);
        return InvariantResult.of("I1", "no lost events",
                "every 2xx-acknowledged command has its event in es__events",
                lostOps + " of " + ackedOps + " acknowledged ops missing; " + ambiguousStored
                        + " unacknowledged ops were stored anyway",
                metrics, violations);
    }

    // ---- I2 ---------------------------------------------------------------------------------------

    /**
     * I2: nothing happens twice. Checked on the event store (one
     * {@code OrderCreated} per order, no more items/completions than were
     * sent, no idempotency key stored twice) and on the side effects
     * (observer deliveries, payment attempts).
     */
    public static InvariantResult i2NoDuplicates(Map<String, OrderLedger> ledgers, Collection<Rows.EsEvent> events,
                                                 Collection<Rows.ObservedRow> observed,
                                                 Collection<Rows.PaymentRow> payments) {
        var expected = expectedOrders(events);
        var violations = new ArrayList<String>();
        int dupCreated = 0;
        int dupItems = 0;
        int dupCompleted = 0;
        int dupPaid = 0;
        for (var e : expected.values()) {
            if (e.created() > 1) {
                dupCreated += e.created() - 1;
                violations.add(e.orderId() + ": " + e.created() + " x " + CREATED);
            }
            if (e.completed() > 1) {
                dupCompleted += e.completed() - 1;
                violations.add(e.orderId() + ": " + e.completed() + " x " + COMPLETED);
            }
            if (e.paid() > 1) {
                dupPaid += e.paid() - 1;
                violations.add(e.orderId() + ": " + e.paid() + " x " + PAID);
            }
            var l = ledgers.get(e.orderId());
            if (l != null) {
                int maxItems = l.itemsAcked + l.itemsIndeterminate;
                if (e.items() > maxItems) {
                    dupItems += e.items() - maxItems;
                    violations.add(e.orderId() + ": " + e.items() + " items stored, at most " + maxItems + " sent");
                }
            }
        }
        var keyCounts = idempotencyKeyCounts(events);
        int dupKeys = 0;
        for (var k : keyCounts.entrySet()) {
            if (k.getValue() > 1) {
                dupKeys++;
                violations.add("idempotency key " + k.getKey() + " stored " + k.getValue() + " times");
            }
        }
        int dupDeliveries = 0;
        if (observed != null) {
            for (var o : observed) {
                if (o.deliveries() > 1) {
                    dupDeliveries += o.deliveries() - 1;
                    violations.add("observer saw seq " + o.seq() + " (" + o.eventName() + ", " + o.orderId() + ") "
                            + o.deliveries() + " times");
                }
            }
        }
        int dupPayments = 0;
        if (payments != null) {
            for (var p : payments) {
                if (p.attempts() > 1) {
                    dupPayments += p.attempts() - 1;
                    violations.add("payment for " + p.orderId() + " executed " + p.attempts() + " times");
                }
            }
        }
        var metrics = new LinkedHashMap<String, Object>();
        metrics.put("duplicateOrderCreated", dupCreated);
        metrics.put("duplicateItemsBeyondSent", dupItems);
        metrics.put("duplicateOrderCompleted", dupCompleted);
        metrics.put("duplicatePaymentProcessed", dupPaid);
        metrics.put("idempotencyKeysStoredTwice", dupKeys);
        metrics.put("duplicateObserverDeliveries", dupDeliveries);
        metrics.put("duplicatePaymentExecutions", dupPayments);
        return InvariantResult.of("I2", "no duplicate events or side effects",
                "one OrderCreated per order; no key stored twice; observer deliveries = 1; payment attempts = 1",
                violations.isEmpty() ? "no duplicates" : violations.size() + " duplicate finding(s)",
                metrics, violations);
    }

    public static Map<String, Integer> idempotencyKeyCounts(Collection<Rows.EsEvent> events) {
        var counts = new LinkedHashMap<String, Integer>();
        for (var e : events) {
            if (e.message() == null) continue;
            var m = IDEMPOTENCY_KEY.matcher(e.message());
            if (m.find()) {
                // key + event type: the same key legitimately appears on a create and
                // on the item it carried ("key#0"), never twice on one type.
                counts.merge(simple(e.eventName()) + ":" + m.group(1), 1, Integer::sum);
            }
        }
        return counts;
    }

    // ---- I3 ---------------------------------------------------------------------------------------

    /**
     * I3: each aggregate's story replays under the domain rules — first event
     * is the creation, one creation, nothing after completion, one context —
     * and no sequence number is used twice.
     */
    public static InvariantResult i3NoFork(Collection<Rows.EsEvent> events, Collection<Long> duplicateSeqsGlobal) {
        var violations = new ArrayList<String>();
        var bySeq = new HashMap<Long, Integer>();
        var stories = new TreeMap<String, List<Rows.EsEvent>>();
        for (var e : events) {
            bySeq.merge(e.seq(), 1, Integer::sum);
            if (!e.is(PAID) && e.aggregateId() != null) {
                stories.computeIfAbsent(e.aggregateId(), k -> new ArrayList<>()).add(e);
            }
        }
        bySeq.forEach((seq, n) -> {
            if (n > 1) violations.add("sequence " + seq + " used by " + n + " events of this run");
        });
        if (duplicateSeqsGlobal != null) {
            for (var s : duplicateSeqsGlobal) {
                if (bySeq.getOrDefault(s, 0) <= 1) violations.add("sequence " + s + " duplicated in es__events");
            }
        }
        int forked = 0;
        for (var entry : stories.entrySet()) {
            var story = entry.getValue();
            story.sort((a, b) -> Long.compare(a.seq(), b.seq()));
            var problems = new ArrayList<String>();
            if (!story.getFirst().is(CREATED)) problems.add("first event is " + simple(story.getFirst().eventName()));
            boolean completed = false;
            int creates = 0;
            String context = null;
            for (var e : story) {
                if (e.is(CREATED)) creates++;
                if (completed && (e.is(ITEM_ADDED) || e.is(COMPLETED))) {
                    problems.add(simple(e.eventName()) + " at seq " + e.seq() + " after completion");
                }
                if (e.is(COMPLETED)) completed = true;
                if (context == null) context = e.context();
                else if (!Objects.equals(context, e.context())) {
                    problems.add("context " + e.context() + " at seq " + e.seq() + " differs from " + context);
                }
            }
            if (creates > 1) problems.add(creates + " creations");
            if (!problems.isEmpty()) {
                forked++;
                violations.add(entry.getKey() + ": " + String.join("; ", problems));
            }
        }
        var metrics = new LinkedHashMap<String, Object>();
        metrics.put("aggregates", stories.size());
        metrics.put("forkedAggregates", forked);
        metrics.put("duplicateSequenceNumbersGlobal", duplicateSeqsGlobal == null ? "n/a" : duplicateSeqsGlobal.size());
        return InvariantResult.of("I3", "no aggregate fork",
                "every story is valid under the aggregate's rules; sequence numbers unique",
                forked + " of " + stories.size() + " aggregates invalid", metrics, violations);
    }

    // ---- I4 ---------------------------------------------------------------------------------------

    /** I4: {@code order_view_<r>} holds only rows whose context is exactly R. */
    public static InvariantResult i4RegionalIsolation(Map<String, List<Rows.ViewRow>> regional) {
        var violations = new ArrayList<String>();
        var metrics = new LinkedHashMap<String, Object>();
        int rows = 0;
        for (var e : regional.entrySet()) {
            int leaked = 0;
            for (var row : e.getValue()) {
                rows++;
                if (!e.getKey().equals(row.context())) {
                    leaked++;
                    violations.add(LabConfig.regionTable(e.getKey()) + " holds " + row.orderId()
                            + " with context " + row.context());
                }
            }
            metrics.put(e.getKey() + ".rows", e.getValue().size());
            if (leaked > 0) metrics.put(e.getKey() + ".foreignRows", leaked);
        }
        metrics.put("rows", rows);
        return InvariantResult.of("I4", "regional isolation",
                "order_view_<r> contains only context R",
                violations.size() + " foreign row(s) in " + rows, metrics, violations);
    }

    // ---- I5 ---------------------------------------------------------------------------------------

    /** I5: the global view is exactly the union of the regional views (same orders, same state). */
    public static InvariantResult i5GlobalCompleteness(List<Rows.ViewRow> global,
                                                       Map<String, List<Rows.ViewRow>> regional) {
        var violations = new ArrayList<String>();
        var union = new LinkedHashMap<String, Rows.ViewRow>();
        for (var rows : regional.values()) {
            for (var r : rows) {
                var prev = union.putIfAbsent(r.orderId(), r);
                if (prev != null) violations.add(r.orderId() + " present in two regional views");
            }
        }
        var globalById = new LinkedHashMap<String, Rows.ViewRow>();
        for (var g : global) globalById.put(g.orderId(), g);
        int missingInGlobal = 0;
        int missingInRegional = 0;
        int mismatched = 0;
        for (var r : union.values()) {
            var g = globalById.get(r.orderId());
            if (g == null) {
                missingInGlobal++;
                violations.add(r.orderId() + " in " + LabConfig.regionTable(r.context()) + " but not in global");
            } else if (!Objects.equals(g.status(), r.status()) || g.itemCount() != r.itemCount()
                    || !Objects.equals(g.context(), r.context())) {
                mismatched++;
                violations.add(r.orderId() + " global=" + g.status() + "/" + g.itemCount() + "/" + g.context()
                        + " regional=" + r.status() + "/" + r.itemCount() + "/" + r.context());
            }
        }
        for (var g : global) {
            if (!union.containsKey(g.orderId())) {
                missingInRegional++;
                violations.add(g.orderId() + " (" + g.context() + ") in global but in no regional view");
            }
        }
        var metrics = new LinkedHashMap<String, Object>();
        metrics.put("globalRows", global.size());
        metrics.put("regionalRows", union.size());
        metrics.put("missingInGlobal", missingInGlobal);
        metrics.put("missingInRegional", missingInRegional);
        metrics.put("mismatched", mismatched);
        return InvariantResult.of("I5", "global completeness",
                "order_view_global = union of order_view_<r> (same ids, status, item count, context)",
                missingInGlobal + " missing in global, " + missingInRegional + " missing regionally, "
                        + mismatched + " mismatched",
                metrics, violations);
    }

    // ---- I6 ---------------------------------------------------------------------------------------

    /**
     * The read models vs. the event store, right now: every order must be in
     * the global view and in its context's regional view (when that context
     * is one of the regions) with the event store's status, item count and
     * last sequence. Empty = converged.
     */
    public static List<String> convergenceGaps(Collection<Rows.EsEvent> events, List<Rows.ViewRow> global,
                                               Map<String, List<Rows.ViewRow>> regional) {
        var gaps = new ArrayList<String>();
        var expected = expectedOrders(events);
        var g = index(global);
        var regionalIdx = new HashMap<String, Map<String, Rows.ViewRow>>();
        regional.forEach((r, rows) -> regionalIdx.put(r, index(rows)));
        for (var e : expected.values()) {
            if (e.created() == 0) continue; // payment of an order outside the scope, or a lost create
            var gr = g.get(e.orderId());
            if (gr == null) {
                gaps.add(e.orderId() + " missing in global");
            } else {
                if (!e.status().equals(gr.status()) || e.items() != gr.itemCount()
                        || gr.lastSeq() != e.lastSeqWithPayment()
                        || (e.paid() > 0 && !"PAID".equals(gr.paymentStatus()))) {
                    gaps.add(e.orderId() + " global " + gr.status() + "/" + gr.itemCount() + "/" + gr.lastSeq()
                            + " != es " + e.status() + "/" + e.items() + "/" + e.lastSeqWithPayment());
                }
            }
            var rIdx = regionalIdx.get(e.context());
            if (rIdx != null) {
                var rr = rIdx.get(e.orderId());
                if (rr == null) {
                    gaps.add(e.orderId() + " missing in " + LabConfig.regionTable(e.context()));
                } else if (!e.status().equals(rr.status()) || e.items() != rr.itemCount()
                        || rr.lastSeq() != e.lastOrderSeq()) {
                    gaps.add(e.orderId() + " " + LabConfig.regionTable(e.context()) + " " + rr.status() + "/"
                            + rr.itemCount() + "/" + rr.lastSeq() + " != es " + e.status() + "/" + e.items() + "/"
                            + e.lastOrderSeq());
                }
            }
        }
        return gaps;
    }

    /**
     * I6: poll {@code snapshots} until the read models match the event store
     * or {@code timeout} passes; report the time it took.
     */
    public static InvariantResult i6Convergence(Supplier<Rows.Snapshot> snapshots, Duration timeout, Duration poll) {
        var start = Instant.now();
        List<String> gaps = List.of("never checked");
        Rows.Snapshot last = null;
        int polls = 0;
        while (true) {
            polls++;
            last = snapshots.get();
            if (last.events() != null && last.global() != null && last.regional() != null) {
                gaps = convergenceGaps(last.events(), last.global(), last.regional());
                if (gaps.isEmpty()) break;
            } else {
                gaps = List.of("snapshot incomplete: " + last.loadErrors());
            }
            if (Duration.between(start, Instant.now()).compareTo(timeout) >= 0) break;
            Cluster.sleep(poll);
        }
        var took = Duration.between(start, Instant.now());
        var metrics = new LinkedHashMap<String, Object>();
        metrics.put("converged", gaps.isEmpty());
        metrics.put("secondsToConverge", gaps.isEmpty() ? took.toMillis() / 1000.0 : null);
        metrics.put("timeoutSeconds", timeout.toSeconds());
        metrics.put("polls", polls);
        metrics.put("remainingGaps", gaps.size());
        if (last != null && last.consumers() != null && last.esHead() != null) {
            var cps = new TreeMap<String, Object>();
            for (var c : last.consumers()) cps.put(c.consumerId(), c.lastSequence());
            metrics.put("esHead", last.esHead());
            metrics.put("checkpoints", cps);
        }
        return InvariantResult.of("I6", "convergence",
                "read models reach the event store's state within " + timeout.toSeconds() + " s",
                gaps.isEmpty() ? "converged in " + took.toMillis() + " ms"
                        : gaps.size() + " order(s) still diverging after " + took.toSeconds() + " s",
                metrics, gaps);
    }

    // ---- I7 ---------------------------------------------------------------------------------------

    /** {@code SUD_AMERICA -> SudAmericaOrderProjector} (mirrors the app's {@code Regions.projectorName}). */
    static String projectorName(String region) {
        var sb = new StringBuilder();
        for (var part : region.toLowerCase(java.util.Locale.ROOT).split("_")) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.append("OrderProjector").toString();
    }

    /** Consumer id prefixes the default topology must have registered. */
    public static List<String> expectedConsumerPrefixes() {
        var out = new ArrayList<String>();
        for (var r : LabConfig.REGIONS) {
            // query-<r>_<R>OrderProjector_1_<R>: one projector class per region (N1)
            out.add("query-" + r.toLowerCase() + "_" + projectorName(r) + "_");
        }
        out.add("cluster-query-global_GlobalOrderProjector_");
        out.add("cluster-saga_PaymentSaga_");
        out.add("cluster-saga_ClusterEventObserver_");
        return out;
    }

    /**
     * I7: after healing, the catalog lists every live bundle, a handler for
     * every command/query, and every consumer.
     */
    public static InvariantResult i7Catalog(Rows.Catalog catalog, List<String> expectedBundles,
                                            List<String> expectedPayloads, List<String> expectedConsumerPrefixes) {
        if (catalog == null) return InvariantResult.error("I7", "catalog", "catalog could not be read");
        var violations = new ArrayList<String>();
        int missingBundles = 0;
        for (var b : expectedBundles) {
            if (!catalog.bundles().containsKey(b)) {
                missingBundles++;
                violations.add("bundle " + b + " missing from core__bundle");
            }
        }
        int missingHandlers = 0;
        for (var p : expectedPayloads) {
            if (catalog.handlersByPayload().getOrDefault(p, Set.of()).isEmpty()) {
                missingHandlers++;
                violations.add("no handler row for " + p);
            }
        }
        // Every live bundle must own at least one handler row. core__component is keyed by the
        // component's simple class name (N1 in FINDINGS.md), so bundles sharing a component class
        // (before the lab's workaround the seven query-<region> bundles all ran RegionOrderProjector) end up with the rows of
        // whichever registered last and the others have none.
        var bundlesWithHandlers = new HashSet<String>();
        catalog.handlersByPayload().values().forEach(bundlesWithHandlers::addAll);
        int bundlesWithoutHandlers = 0;
        for (var b : expectedBundles) {
            if (catalog.bundles().containsKey(b) && !bundlesWithHandlers.contains(b)) {
                bundlesWithoutHandlers++;
                violations.add("bundle " + b + " has no core__handler rows");
            }
        }
        int missingConsumers = 0;
        for (var prefix : expectedConsumerPrefixes) {
            boolean found = catalog.consumers().keySet().stream().anyMatch(c -> c != null && c.startsWith(prefix));
            if (!found) {
                missingConsumers++;
                violations.add("no core__consumer row for " + prefix + "*");
            }
        }
        var metrics = new LinkedHashMap<String, Object>();
        metrics.put("bundles", catalog.bundles().size());
        metrics.put("consumers", catalog.consumers().size());
        metrics.put("missingBundles", missingBundles);
        metrics.put("missingHandlers", missingHandlers);
        metrics.put("bundlesWithoutHandlers", bundlesWithoutHandlers);
        metrics.put("missingConsumers", missingConsumers);
        metrics.put("bundleInstances", new TreeMap<>(catalog.bundles()));
        return InvariantResult.of("I7", "catalog matches live bundles",
                "core__bundle / core__handler / core__consumer list every live bundle, handler and consumer",
                missingBundles + " bundle(s), " + missingHandlers + " handler(s), " + missingConsumers
                        + " consumer(s) missing; " + bundlesWithoutHandlers + " bundle(s) without handler rows",
                metrics, violations);
    }

    // ---- I8 ---------------------------------------------------------------------------------------

    /** I8: per-region command latency, observed (never pass/fail). */
    public static InvariantResult i8Latency(Collection<RequestRecord> records) {
        var metrics = new LinkedHashMap<String, Object>(LatencyStats.byRegion(records));
        return InvariantResult.observedOnly("I8", "command latency per region",
                "recorded as a baseline (p50/p95/p99), not a pass/fail",
                LatencyStats.overall(records).toString(), metrics);
    }

    // ---- all --------------------------------------------------------------------------------------

    /**
     * I1..I5, I7, I8 on one snapshot, plus I6 by polling. A section of the
     * snapshot that could not be read turns its invariants into ERROR rather
     * than a false PASS.
     */
    public static List<InvariantResult> checkAll(Supplier<Rows.Snapshot> snapshots, Collection<RequestRecord> records,
                                                 Duration convergenceTimeout) {
        var out = new ArrayList<InvariantResult>();
        var i6 = i6Convergence(snapshots, convergenceTimeout, Duration.ofSeconds(3));
        var snap = snapshots.get();
        var ledgers = OrderLedger.fromRecords(records);
        if (snap.events() == null) {
            out.add(InvariantResult.error("I1", "no lost events", "es db unreadable: " + snap.loadErrors()));
            out.add(InvariantResult.error("I2", "no duplicates", "es db unreadable: " + snap.loadErrors()));
            out.add(InvariantResult.error("I3", "no aggregate fork", "es db unreadable: " + snap.loadErrors()));
        } else {
            out.add(i1NoLostEvents(ledgers, snap.events()));
            out.add(i2NoDuplicates(ledgers, snap.events(), snap.observed(), snap.payments()));
            out.add(i3NoFork(snap.events(), snap.duplicateSeqsGlobal()));
        }
        if (snap.regional() == null || snap.global() == null) {
            out.add(InvariantResult.error("I4", "regional isolation", "app db unreadable: " + snap.loadErrors()));
            out.add(InvariantResult.error("I5", "global completeness", "app db unreadable: " + snap.loadErrors()));
        } else {
            out.add(i4RegionalIsolation(snap.regional()));
            out.add(i5GlobalCompleteness(snap.global(), snap.regional()));
        }
        out.add(i6);
        out.add(i7Catalog(snap.catalog(), LabConfig.EXPECTED_BUNDLES, LabConfig.EXPECTED_HANDLED_PAYLOADS,
                expectedConsumerPrefixes()));
        out.add(i8Latency(records));
        return out;
    }

    private static Map<String, Rows.ViewRow> index(List<Rows.ViewRow> rows) {
        var m = new HashMap<String, Rows.ViewRow>();
        if (rows != null) for (var r : rows) m.put(r.orderId(), r);
        return m;
    }

    static String simple(String eventName) {
        if (eventName == null) return "null";
        var i = eventName.lastIndexOf('.');
        return i < 0 ? eventName : eventName.substring(i + 1);
    }
}
