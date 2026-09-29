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
                List.of(new Rows.ObservedRow(3, "OrderItemAddedEvent", "o1", 2)),
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
    void i4FlagsForeignContextRows() {
        var r = Invariants.i4RegionalIsolation(Map.of(
                "SUD_AMERICA", List.of(view("a", "SUD_AMERICA", "CREATED", 0, 1), view("b", "SUDXAMERICA", "CREATED", 0, 2)),
                "EUROPA", List.of(view("c", "EUROPA", "CREATED", 0, 3))));
        assertThat(r.status()).isEqualTo(InvariantResult.Status.FAIL);
        assertThat(r.violationCount()).isEqualTo(1);
        assertThat(r.violations().getFirst()).contains("order_view_sud_america").contains("SUDXAMERICA");
    }

    @Test
    void i5ComparesGlobalWithTheUnionOfRegions() {
        var regional = Map.of(
                "EUROPA", List.of(view("a", "EUROPA", "CREATED", 1, 2)),
                "CINA", List.of(view("b", "CINA", "COMPLETED", 0, 5), view("c", "CINA", "CREATED", 0, 6)));
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
        var converged = Invariants.convergenceGaps(events,
                List.of(new Rows.ViewRow("a", "EUROPA", "COMPLETED", 1, 1, "PAID", 4, 4, 0),
                        view("s", "SUDXAMERICA", "CREATED", 0, 5)),
                Map.of("EUROPA", List.of(view("a", "EUROPA", "COMPLETED", 1, 3))));
        assertThat(converged).isEmpty();

        var lagging = Invariants.convergenceGaps(events,
                List.of(view("a", "EUROPA", "COMPLETED", 1, 3)),
                Map.of("EUROPA", List.of(view("a", "EUROPA", "CREATED", 1, 2))));
        assertThat(lagging).hasSize(3); // global missing payment, regional behind, synthetic missing in global
    }

    @Test
    void i6PollsUntilConvergedAndReportsTheTime() {
        var events = List.of(ev(1, "a", "OrderCreatedEvent", "EUROPA"));
        var calls = new AtomicInteger();
        var r = Invariants.i6Convergence(() -> {
            var n = calls.incrementAndGet();
            var rows = n < 2 ? List.<Rows.ViewRow>of() : List.of(view("a", "EUROPA", "CREATED", 0, 1));
            return new Rows.Snapshot("p", events, 1L, List.of(), Map.of("EUROPA", rows), rows, List.of(), List.of(),
                    List.of(new Rows.ConsumerStateRow("c", "PROJECTOR", 1, "now")), null, List.of());
        }, Duration.ofSeconds(5), Duration.ofMillis(10));
        assertThat(r.status()).isEqualTo(InvariantResult.Status.PASS);
        assertThat(r.metrics()).containsEntry("converged", true).containsEntry("polls", 2);
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
                Map.of("cluster-command", "cell-core-eu-cluster-command"),
                Map.of("CreateOrderCommand", Set.of("cluster-command")),
                Map.of("query-europa_RegionOrderProjector_1_EUROPA", "i1"));
        var r = Invariants.i7Catalog(catalog, List.of("cluster-command", "query-europa"),
                List.of("CreateOrderCommand", "FindGlobalOrderQuery"),
                List.of("query-europa_RegionOrderProjector_", "cluster-saga_PaymentSaga_"));
        assertThat(r.metrics()).containsEntry("missingBundles", 1).containsEntry("missingHandlers", 1)
                .containsEntry("missingConsumers", 1);
        assertThat(Invariants.i7Catalog(null, List.of(), List.of(), List.of()).status())
                .isEqualTo(InvariantResult.Status.ERROR);
    }

    @Test
    void expectedConsumerPrefixesCoverEveryRegionWithItsUnderscore() {
        assertThat(Invariants.expectedConsumerPrefixes())
                .contains("query-sud_america_RegionOrderProjector_", "query-australia_nz_RegionOrderProjector_")
                .hasSize(LabConfig.REGIONS.size() + 3);
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
