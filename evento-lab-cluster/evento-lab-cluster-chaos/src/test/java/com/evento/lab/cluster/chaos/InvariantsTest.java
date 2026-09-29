package com.evento.lab.cluster.chaos;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure invariant computations over in-memory rows: no stack needed. */
class InvariantsTest {

    private static Rows.EsEvent ev(long seq, String agg, String name, String ctx) {
        return new Rows.EsEvent(seq, agg, name, ctx, null);
    }

    private static Rows.EsEvent ev(long seq, String agg, String name, String ctx, String key) {
        // shape of the stored message: the payload is JSON inside a JSON string
        var msg = "{\"serializedPayload\":{\"serializedObject\":\"[\\\"x.Y\\\",{\\\"orderId\\\":\\\"" + agg
                + "\\\",\\\"idempotencyKey\\\":\\\"" + key + "\\\"}]\"}}";
        return new Rows.EsEvent(seq, agg, name, ctx, msg);
    }

    private static RequestRecord rec(String op, String orderId, RequestRecord.Outcome outcome) {
        return new RequestRecord(0, 10, "EUROPA", op, orderId, outcome == RequestRecord.Outcome.ACK ? 200 : 504,
                outcome, outcome == RequestRecord.Outcome.ACK ? "ok" : "timeout", null, null, null);
    }

    private static Rows.ViewRow view(String id, String ctx, String status, int items, long lastSeq) {
        return new Rows.ViewRow(id, ctx, status, items, items, null, lastSeq, 1 + items, 0);
    }

    @Test
    void expectedOrdersFoldsPaymentsIntoTheirOrder() {
        var events = List.of(
                ev(1, "o1", "OrderCreatedEvent", "EUROPA"),
                ev(2, "o1", "OrderItemAddedEvent", "EUROPA"),
                ev(3, "o1", "OrderCompletedEvent", "EUROPA"),
                ev(4, "payment-o1", "PaymentProcessedEvent", "EUROPA"));
        var e = Invariants.expectedOrders(events).get("o1");
        assertThat(e.created()).isEqualTo(1);
        assertThat(e.items()).isEqualTo(1);
        assertThat(e.completed()).isEqualTo(1);
        assertThat(e.paid()).isEqualTo(1);
        assertThat(e.lastOrderSeq()).isEqualTo(3);
        assertThat(e.lastSeqWithPayment()).isEqualTo(4);
        assertThat(e.context()).isEqualTo("EUROPA");
    }

    @Test
    void i1FlagsAcknowledgedButMissing() {
        var ledgers = OrderLedger.fromRecords(List.of(
                rec("create", "o1", RequestRecord.Outcome.ACK),
                rec("add_item", "o1", RequestRecord.Outcome.ACK),
                rec("add_item", "o1", RequestRecord.Outcome.ACK),
                rec("create", "o2", RequestRecord.Outcome.ACK),
                rec("create", "o3", RequestRecord.Outcome.NO_RESPONSE)));
        var events = List.of(
                ev(1, "o1", "OrderCreatedEvent", "EUROPA"),
                ev(2, "o1", "OrderItemAddedEvent", "EUROPA"),
                ev(3, "o3", "OrderCreatedEvent", "EUROPA"));
        var r = Invariants.i1NoLostEvents(ledgers, events);
        assertThat(r.status()).isEqualTo(InvariantResult.Status.FAIL);
        assertThat(r.metrics()).containsEntry("lostOps", 2).containsEntry("acknowledgedOps", 4)
                .containsEntry("storedWithUnknownOutcome", 1);
        assertThat(r.violations()).anyMatch(v -> v.startsWith("o1:")).anyMatch(v -> v.startsWith("o2:"));
    }

    @Test
    void i1PassesWhenEverythingIsStored() {
        var ledgers = OrderLedger.fromRecords(List.of(rec("create", "o1", RequestRecord.Outcome.ACK)));
        var r = Invariants.i1NoLostEvents(ledgers, List.of(ev(1, "o1", "OrderCreatedEvent", "EUROPA")));
        assertThat(r.status()).isEqualTo(InvariantResult.Status.PASS);
    }

    @Test
    void i2FindsDuplicatesInStoreAndSideEffects() {
        var ledgers = OrderLedger.fromRecords(List.of(
                rec("create", "o1", RequestRecord.Outcome.ACK),
                rec("add_item", "o1", RequestRecord.Outcome.ACK)));
        var events = List.of(
                ev(1, "o1", "OrderCreatedEvent", "EUROPA", "k-create-1"),
                ev(2, "o1", "OrderCreatedEvent", "EUROPA", "k-create-2"),
                ev(3, "o1", "OrderItemAddedEvent", "EUROPA", "k-item-000"),
                ev(4, "o1", "OrderItemAddedEvent", "EUROPA", "k-item-000"));
        var r = Invariants.i2NoDuplicates(ledgers, events,
                List.of(new Rows.ObservedRow("order-observer", 3, "OrderItemAddedEvent", "o1", 2),
                        new Rows.ObservedRow("order-observer-regional", 3, "OrderItemAddedEvent", "o1", 1)),
                List.of(new Rows.PaymentRow("o1", 3)));
        assertThat(r.status()).isEqualTo(InvariantResult.Status.FAIL);
        assertThat(r.metrics())
                .containsEntry("duplicateOrderCreated", 1)
                .containsEntry("duplicateItemsBeyondSent", 1)
                .containsEntry("idempotencyKeysStoredTwice", 1)
                .containsEntry("duplicateObserverDeliveries", 1)
                .containsEntry("duplicatePaymentExecutions", 2);
    }

    @Test
    void idempotencyKeyIsReadFromTheEscapedPayload() {
        var counts = Invariants.idempotencyKeyCounts(List.of(
                ev(1, "o1", "OrderCreatedEvent", "EUROPA", "3f2c9a1e-aaaa-bbbb-cccc-000000000001"),
                ev(2, "o1", "OrderItemAddedEvent", "EUROPA", "3f2c9a1e-aaaa-bbbb-cccc-000000000001#0"),
                new Rows.EsEvent(3, "o2", "OrderCreatedEvent", "EUROPA", "{\"idempotencyKey\":null}")));
        assertThat(counts).containsOnlyKeys(
                "OrderCreatedEvent:3f2c9a1e-aaaa-bbbb-cccc-000000000001",
                "OrderItemAddedEvent:3f2c9a1e-aaaa-bbbb-cccc-000000000001#0");
    }

    @Test
    void i3DetectsForksAndDuplicateSequences() {
        var events = new ArrayList<>(List.of(
                ev(1, "ok", "OrderCreatedEvent", "CINA"),
                ev(2, "ok", "OrderItemAddedEvent", "CINA"),
                ev(3, "ok", "OrderCompletedEvent", "CINA"),
                ev(4, "fork", "OrderCreatedEvent", "CINA"),
                ev(5, "fork", "OrderCompletedEvent", "CINA"),
                ev(6, "fork", "OrderItemAddedEvent", "CINA"),
                ev(7, "twice", "OrderCreatedEvent", "INDIA"),
                ev(8, "twice", "OrderCreatedEvent", "INDIA"),
                ev(9, "headless", "OrderItemAddedEvent", "INDIA"),
                ev(10, "ctx", "OrderCreatedEvent", "INDIA"),
                ev(11, "ctx", "OrderItemAddedEvent", "CINA"),
                ev(12, "payment-ok", "PaymentProcessedEvent", "CINA")));
        var r = Invariants.i3NoFork(events, List.of(99L));
        assertThat(r.metrics()).containsEntry("aggregates", 5).containsEntry("forkedAggregates", 4);
        assertThat(r.violations()).anyMatch(v -> v.startsWith("fork:") && v.contains("after completion"))
                .anyMatch(v -> v.startsWith("twice:") && v.contains("2 creations"))
                .anyMatch(v -> v.startsWith("headless:") && v.contains("first event"))
                .anyMatch(v -> v.startsWith("ctx:") && v.contains("context"))
                .anyMatch(v -> v.contains("sequence 99"));
    }

    @Test
    void i4FlagsForeignContextRowsPerCellDatabase() {
        var r = Invariants.i4RegionalIsolation(Map.of(
                "AM", List.of(view("a", "SUD_AMERICA", "CREATED", 0, 1), view("b", "SUDXAMERICA", "CREATED", 0, 2),
                        view("d", "AMERICA", "CREATED", 0, 4)),
                "EU", List.of(view("c", "EUROPA", "CREATED", 0, 3), view("e", "AFRICA", "CREATED", 0, 5)),
                "APAC", List.of(view("f", "EUROPA", "CREATED", 0, 6))));
        assertThat(r.status()).isEqualTo(InvariantResult.Status.FAIL);
        assertThat(r.violationCount()).isEqualTo(2);
        assertThat(r.violations()).anyMatch(v -> v.contains("app-pg-am.order_view") && v.contains("SUDXAMERICA"))
                .anyMatch(v -> v.contains("app-pg-apac.order_view") && v.contains("EUROPA"));
        assertThat(r.metrics()).containsEntry("EU.rows", 2).containsEntry("AM.foreignRows", 1);
    }

    @Test
    void i5ComparesGlobalWithTheUnionOfRegions() {
        var regional = Map.of(
                "EU", List.of(view("a", "EUROPA", "CREATED", 1, 2)),
                "APAC", List.of(view("b", "CINA", "COMPLETED", 0, 5), view("c", "CINA", "CREATED", 0, 6)));
        var global = List.of(view("a", "EUROPA", "CREATED", 1, 2), view("b", "CINA", "CREATED", 0, 4),
                view("z", "INDIA", "CREATED", 0, 7));
        var r = Invariants.i5GlobalCompleteness(global, regional);
        assertThat(r.metrics()).containsEntry("missingInGlobal", 1).containsEntry("missingInRegional", 1)
                .containsEntry("mismatched", 1);
    }

    @Test
    void convergenceGapsCompareViewsWithTheStore() {
        var events = List.of(
                ev(1, "a", "OrderCreatedEvent", "EUROPA"),
                ev(2, "a", "OrderItemAddedEvent", "EUROPA"),
                ev(3, "a", "OrderCompletedEvent", "EUROPA"),
                ev(4, "payment-a", "PaymentProcessedEvent", "EUROPA"),
                ev(5, "s", "OrderCreatedEvent", "SUDXAMERICA"));
        var paidRow = new Rows.ViewRow("a", "EUROPA", "COMPLETED", 1, 1, "PAID", 4, 4, 0);
        var converged = Invariants.convergenceGaps(events,
                List.of(paidRow, view("s", "SUDXAMERICA", "CREATED", 0, 5)),
                Map.of("EU", List.of(paidRow)));
        assertThat(converged).isEmpty();

        var lagging = Invariants.convergenceGaps(events,
                List.of(view("a", "EUROPA", "COMPLETED", 1, 3)),
                Map.of("EU", List.of(view("a", "EUROPA", "CREATED", 1, 2))));
        // global missing payment, regional behind, synthetic missing in global
        assertThat(lagging).hasSize(3).anyMatch(g -> g.contains("app-pg-eu.order_view"));
    }

    @Test
    void i6PollsUntilConvergedAndReportsTheTime() {
        var events = List.of(ev(1, "a", "OrderCreatedEvent", "EUROPA"));
        var calls = new AtomicInteger();
        var r = Invariants.i6Convergence(() -> {
            var n = calls.incrementAndGet();
            var rows = n < 2 ? List.<Rows.ViewRow>of() : List.of(view("a", "EUROPA", "CREATED", 0, 1));
            return new Rows.Snapshot("p", events, 1L, List.of(), Map.of("EU", rows), rows, List.of(), List.of(),
                    List.of(new Rows.ConsumerStateRow("EU", "c", "PROJECTOR", 1, "now")), null, List.of());
        }, Duration.ofSeconds(5), Duration.ofMillis(10));
        assertThat(r.status()).isEqualTo(InvariantResult.Status.PASS);
        assertThat(r.metrics()).containsEntry("converged", true).containsEntry("polls", 2)
                .containsEntry("checkpoints", new java.util.TreeMap<>(Map.of("EU/c", 1L)));
    }

    @Test
    void i6TimesOut() {
        var r = Invariants.i6Convergence(() -> new Rows.Snapshot("p", List.of(ev(1, "a", "OrderCreatedEvent", "EUROPA")),
                        1L, List.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), null, List.of()),
                Duration.ofMillis(50), Duration.ofMillis(10));
        assertThat(r.status()).isEqualTo(InvariantResult.Status.FAIL);
        assertThat(r.metrics()).containsEntry("converged", false);
    }

    @Test
    void i7ChecksBundlesHandlersAndConsumers() {
        var catalog = new Rows.Catalog(
                Map.of("order-command", "order-command-eu", "order-gateway", "gateway-europa"),
                Map.of("CreateOrderCommand", Set.of("order-command")),
                Map.of("order-query_OrderProjector_1_EUROPA", "order-query-eu"));
        var r = Invariants.i7Catalog(catalog, List.of("order-command", "order-query", "order-gateway"),
                List.of("CreateOrderCommand", "FindGlobalOrderQuery"),
                List.of("order-query_OrderProjector_1_EUROPA", "order-saga_PaymentSaga_1_EUROPA"));
        // the gateway owns no handler by design and is not flagged
        assertThat(r.metrics()).containsEntry("missingBundles", 1).containsEntry("missingHandlers", 1)
                .containsEntry("missingConsumers", 1).containsEntry("bundlesWithoutHandlers", 0);
        // G8 / N1: a live bundle left without handler rows is flagged
        var lost = new Rows.Catalog(
                Map.of("order-query", "a", "order-saga", "b"),
                Map.of("OrderCreatedEvent", Set.of("order-saga")),
                Map.of());
        assertThat(Invariants.i7Catalog(lost, List.of("order-query", "order-saga"), List.of(), List.of())
                .metrics()).containsEntry("bundlesWithoutHandlers", 1);
        assertThat(Invariants.i7Catalog(null, List.of(), List.of(), List.of()).status())
                .isEqualTo(InvariantResult.Status.ERROR);
    }

    @Test
    void expectedConsumerIdsAreOneEnginePerComponentAndContext() {
        assertThat(Invariants.expectedConsumerPrefixes())
                .contains("order-query_OrderProjector_1_SUD_AMERICA", "order-query_OrderProjector_1_AUSTRALIA_NZ",
                        "order-query_OrderProjector_1_*", "order-query_CatalogProjector_1_ALL",
                        "order-saga_PaymentSaga_1_CINA", "order-observer_NotificationObserver_1_*")
                // the global lane is the literal ALL context, never the wildcard
                .doesNotContain("order-query_CatalogProjector_1_*")
                .hasSize(2 * LabConfig.REGIONS.size() + 3);
    }

    @Test
    void checkAllTurnsUnreadableSectionsIntoErrors() {
        var snap = new Rows.Snapshot("p", null, null, null, null, null, null, null, null, null, List.of("es db: down"));
        var results = Invariants.checkAll(() -> snap, List.of(), Duration.ofMillis(10));
        assertThat(results).extracting(InvariantResult::id)
                .containsExactly("I1", "I2", "I3", "I4", "I5", "I6", "I7", "I8");
        assertThat(results.subList(0, 5)).allMatch(r -> r.status() == InvariantResult.Status.ERROR);
        assertThat(results.get(7).status()).isEqualTo(InvariantResult.Status.OBSERVED);
    }
}
