package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.Invariants;
import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.RequestRecord;
import com.evento.lab.cluster.chaos.Rows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * S18: the G5 residual window, hit on purpose. A payment's handler commits its side effect
 * (the {@code payments} row) and then, before the server stores its {@code PaymentProcessedEvent},
 * the server loses the event store: {@code evento-apac}'s link to the ES primary
 * ({@code apac-to-es-pg-eu}) is cut while the handlers are still answering. The lab's payment
 * service answers {@code LAB_PAYMENT_SLOW_MS} after its commit for orders whose id contains
 * {@code slowpay}, which makes the gap wide enough to cut into.
 *
 * <p>The append (or the lock check before it) fails, the claim is released or expires, the
 * saga's event is redelivered and its {@code ProcessPaymentCommand} retried with the same derived
 * key. With the bundle's outcome store (the G5 inbox fix) the retry is answered from the outcome
 * recorded in the payment's own transaction: one payment, one event. Without it (images before the
 * fix, or {@code EVENTO_SERVICE_OUTCOME_STORE=off}) the handler runs again: {@code attempts = 2}.
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S18ResidualWindowTest extends AbstractScenario {

    static final String ES_LINK = "apac-to-es-pg-eu";
    static final String SLOW_MARKER = "slowpay";

    @Override
    protected String id() {
        return "S18";
    }

    @Override
    protected String title() {
        return "Residual window: the server loses the event store after the payment ran";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G5");
    }

    @Test
    void residualWindow() throws Exception {
        scenario(() -> {
            int n = LabConfig.envInt("S18_ORDERS", 6);
            var cutFor = envSeconds("S18_CUT_S", 20);
            var region = System.getenv().getOrDefault("S18_REGION", "CINA");
            report.param("orders", n);
            report.param("cutSeconds", cutFor.toSeconds());
            report.param("region", region);
            report.param("esLink", ES_LINK);
            report.param("outcomeStoreEnv", System.getenv().getOrDefault("EVENTO_SERVICE_OUTCOME_STORE", "jdbc (default)"));
            onCleanup(() -> toxi.setEnabled(ES_LINK, true));

            var orders = new ArrayList<String>();
            for (int i = 0; i < n; i++) {
                var id = gateway.newOrderId(prefix + SLOW_MARKER + "-", region);
                var created = gateway.create(region, id, UUID.randomUUID().toString());
                if (created.outcome() != RequestRecord.Outcome.ACK) continue;
                if (gateway.addItem(region, id, "sku-1", 1, UUID.randomUUID().toString()).outcome()
                        == RequestRecord.Outcome.ACK) {
                    orders.add(id);
                }
            }
            report.event("created " + orders.size() + " " + region + " orders with an item");
            if (orders.isEmpty()) {
                report.harnessError("no order could be created");
                return;
            }
            var since = Instant.now();
            for (var id : orders) gateway.complete(region, id, UUID.randomUUID().toString());
            report.event("completed them: the saga pays each one (the handler answers "
                    + System.getenv().getOrDefault("LAB_PAYMENT_SLOW_MS", "4000") + " ms after its commit)");

            // Cut once the payments have committed and their handlers are still answering.
            // The first committed payment answers LAB_PAYMENT_SLOW_MS later: cut at most 1.5 s after it.
            Set<String> paidAtCut = new LinkedHashSet<>();
            long deadline = System.currentTimeMillis() + 30_000;
            long firstSeen = -1;
            while (System.currentTimeMillis() < deadline) {
                paidAtCut = attempts(prefix + SLOW_MARKER).keySet();
                if (paidAtCut.size() >= orders.size()) break;
                if (!paidAtCut.isEmpty()) {
                    if (firstSeen < 0) firstSeen = System.currentTimeMillis();
                    else if (System.currentTimeMillis() - firstSeen > 1_500) break;
                }
                Cluster.sleep(Duration.ofMillis(200));
            }
            toxi.setEnabled(ES_LINK, false);
            var cutAt = Instant.now();
            report.event("cut " + ES_LINK + " with " + paidAtCut.size() + " of " + orders.size()
                    + " payments committed and still answering");
            var storedAtCut = paymentEvents(orders);
            Cluster.sleep(cutFor);
            var storedAtHeal = paymentEvents(orders);
            toxi.setEnabled(ES_LINK, true);
            report.event("healed " + ES_LINK + " after " + cutFor.toSeconds() + " s");

            // The residual window: paid before the cut, no event when the link came back.
            var window = new ArrayList<String>();
            for (var id : paidAtCut) {
                if (storedAtCut.getOrDefault(id, 0) == 0 && storedAtHeal.getOrDefault(id, 0) == 0) window.add(id);
            }
            report.metric("paidBeforeCut", paidAtCut.size());
            report.metric("eventStoredBeforeCut", storedAtCut.values().stream().filter(c -> c > 0).count());
            report.metric("residualWindowHits", window.size());

            // Convergence: the saga retries until every order has its PaymentProcessedEvent.
            var convergence = envSeconds("S18_CONVERGENCE_S", 300);
            long until = System.currentTimeMillis() + convergence.toMillis();
            while (System.currentTimeMillis() < until) {
                var current = paymentEvents(orders);
                if (orders.stream().allMatch(id -> current.getOrDefault(id, 0) > 0)) break;
                Cluster.sleep(Duration.ofSeconds(5));
            }
            var convergedAfter = Duration.between(cutAt, Instant.now()).toSeconds();
            Cluster.sleep(Duration.ofSeconds(5));
            var events = paymentEvents(orders);
            var attempts = attempts(prefix + SLOW_MARKER);
            report.metric("convergedSecondsAfterCut", convergedAfter);
            report.metric("paymentAttempts", attempts);
            report.metric("paymentEvents", events);

            int extraPayments = attempts.values().stream().mapToInt(a -> Math.max(0, a - 1)).sum();
            int extraInWindow = window.stream().mapToInt(id -> Math.max(0, attempts.getOrDefault(id, 0) - 1)).sum();
            int unpaid = (int) orders.stream().filter(id -> events.getOrDefault(id, 0) == 0).count();
            int twice = (int) orders.stream().filter(id -> events.getOrDefault(id, 0) > 1).count();
            report.metric("duplicatePaymentExecutions", extraPayments);
            report.metric("duplicatePaymentExecutionsInWindow", extraInWindow);

            var serverMarkers = markers(List.of("evento-apac", "evento-eu", "evento-am"), since, List.of(
                    "event=idempotent_replay", "event=idempotency_key_in_progress",
                    "event=idempotent_duplicate_execution", "event=idempotency_claim_release_failed"));
            var appMarkers = markers(List.of("order-command-apac", "order-command-eu", "order-command-am"), since,
                    List.of("event=service_outcome_replayed", "event=service_outcome_duplicate_execution",
                            "event=service_outcome_record_failed", "serviceOutcomeStore"));
            report.metric("serviceOutcomeReplayed", total(appMarkers, "event=service_outcome_replayed"));

            report.observe("the residual window was hit",
                    "at least one payment committed while its server could not store its event",
                    window.size() + " of " + paidAtCut.size() + " payments committed before the cut had no event at "
                            + "the heal: " + window,
                    window.isEmpty() ? null : Boolean.TRUE);
            report.observe("a payment whose event could not be stored is not executed again",
                    "0 extra executions: the retry is answered from the outcome recorded with the payment (G5 inbox)",
                    "the saga's retry runs the payment again (G5 residual window): attempts = 2",
                    extraInWindow + " extra execution(s) among the " + window.size() + " in the window; "
                            + extraPayments + " overall; attempts " + attempts,
                    window.isEmpty() ? null : extraPayments == 0, extraPayments > 0,
                    window.isEmpty() ? "No payment was caught in the window; raise LAB_PAYMENT_SLOW_MS." : null);
            report.observe("every payment is stored exactly once",
                    "one PaymentProcessedEvent per order once the saga's retries are done",
                    unpaid + " order(s) without the event after " + convergence.toSeconds() + " s, " + twice
                            + " with two; events " + events,
                    unpaid == 0 && twice == 0);
        });
    }

    /** Payment attempts per order (all cells), for orders whose id starts with {@code idPrefix}. */
    private Map<String, Integer> attempts(String idPrefix) throws Exception {
        var out = new LinkedHashMap<String, Integer>();
        for (var cell : LabConfig.CELLS) {
            try (var c = db.app(cell)) {
                for (Rows.PaymentRow p : db.payments(c, idPrefix)) out.merge(p.orderId(), p.attempts(), Integer::sum);
            }
        }
        return out;
    }

    /** {@code PaymentProcessedEvent}s stored per order. */
    private Map<String, Integer> paymentEvents(List<String> orders) throws Exception {
        var out = new LinkedHashMap<String, Integer>();
        for (var id : orders) {
            int count = 0;
            for (var e : db.eventsContaining(id)) {
                if (e.is(Invariants.PAID) && e.aggregateId() != null
                        && e.aggregateId().equals(Invariants.PAYMENT_PREFIX + id)) count++;
            }
            out.put(id, count);
        }
        return out;
    }
}
