package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.RequestRecord;
import com.evento.lab.cluster.chaos.Rows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * S14: the client retries, once and through another cell, every command that
 * failed because its server was killed mid-request, with the same
 * {@code X-Idempotency-Key}. Three probe kinds; the lab app also copies the key
 * into the event, so every stored copy can be counted:
 * <ul>
 *   <li>create with a gateway-generated id;</li>
 *   <li>create with a client-chosen id;</li>
 *   <li>add item.</li>
 * </ul>
 *
 * <p>Before G5 the key stops at the payload: a command stored before its reply
 * got lost is stored again (a second order, the item twice), or, for the
 * client-chosen id, refused by the aggregate (409, a domain-level dedupe that
 * tells the client "failed" about an order that exists). With G5 the gateway
 * passes the header as the Evento idempotency key: the retry, on whichever
 * server it lands, is answered with the stored event (2xx, no second copy).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S14RetryStormTest extends AbstractScenario {

    private record Probe(String kind, String key, String orderId) {
    }

    @Override
    protected String id() {
        return "S14";
    }

    @Override
    protected String title() {
        return "Retry storm with idempotency keys";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G5", "G4");
    }

    @Test
    void retryStorm() throws Exception {
        scenario(() -> {
            var m = LabConfig.envInt("S14_PROBES_PER_KIND", 8);
            var killDelay = Duration.ofMillis(LabConfig.envInt("S14_KILL_DELAY_MS", 150));
            var retryVia = System.getenv().getOrDefault("S14_RETRY_REGION", "EUROPA");
            report.param("probesPerKind", m);
            report.param("killDelayMs", killDelay.toMillis());
            report.param("retryVia", retryVia);
            onCleanup(() -> {
                cluster.ensureRunning("evento-apac");
                cluster.waitReady("evento-apac", Duration.ofSeconds(420));
            });

            var existing = new ArrayList<String>();
            for (int i = 0; i < m; i++) {
                var id = gateway.newOrderId(prefix + "item-", "CINA");
                if (gateway.create("CINA", id, UUID.randomUUID().toString()).outcome() == RequestRecord.Outcome.ACK) {
                    existing.add(id);
                }
            }
            report.event("pre-created " + existing.size() + " CINA orders for the add-item probes");

            var probes = new ArrayList<Probe>();
            for (int i = 0; i < m; i++) probes.add(new Probe("create-auto-id", UUID.randomUUID().toString(), null));
            for (int i = 0; i < m; i++) {
                probes.add(new Probe("create-client-id", UUID.randomUUID().toString(),
                        gateway.newOrderId(prefix + "cid-", "CINA")));
            }
            for (var id : existing) probes.add(new Probe("add-item", UUID.randomUUID().toString(), id));

            var first = Collections.synchronizedMap(new LinkedHashMap<Probe, RequestRecord>());
            try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new ArrayList<Future<?>>();
                for (var p : probes) {
                    futures.add(exec.submit(() -> first.put(p, send(p, "CINA", false))));
                }
                Cluster.sleep(killDelay);
                cluster.kill("evento-apac");
                report.event("killed evento-apac " + killDelay.toMillis() + " ms after firing " + probes.size()
                        + " commands at CINA");
                for (var f : futures) f.get();
            }
            var firstFailed = first.entrySet().stream()
                    .filter(e -> e.getValue().outcome() != RequestRecord.Outcome.ACK).map(Map.Entry::getKey).toList();
            var timedOut = first.values().stream()
                    .filter(r -> r.outcome() != RequestRecord.Outcome.ACK && r.latencyMs() >= 25_000).count();
            report.metric("firstAttempt", Map.of("sent", probes.size(), "failed", firstFailed.size(),
                    "failedAfter25s", timedOut, "kinds", kinds(first.values())));

            // The G5 question needs a command in the ambiguous window: failed for the client but
            // stored anyway. Count them before retrying; with none, a clean result proves nothing (L17).
            // Late appends of a request the dead server already handed to the database are still
            // possible, so wait a little first.
            Cluster.sleep(Duration.ofSeconds(3));
            var ambiguous = new ArrayList<Probe>();
            for (var p : firstFailed) if (!db.eventsContaining(p.key()).isEmpty()) ambiguous.add(p);
            report.metric("failedButStoredBeforeRetry", ambiguous.size());

            var retries = new LinkedHashMap<Probe, RequestRecord>();
            for (var p : firstFailed) retries.put(p, send(p, retryVia, true));
            report.event("retried " + retries.size() + " failed commands via " + retryVia + ": " + kinds(retries.values()));

            Cluster.sleep(Duration.ofSeconds(5));
            var perKind = new LinkedHashMap<String, Map<String, Integer>>();
            var duplicates = new ArrayList<String>();
            int lost = 0;
            for (var p : probes) {
                List<Rows.EsEvent> stored = db.eventsContaining(p.key());
                var k = perKind.computeIfAbsent(p.kind(), x -> new LinkedHashMap<>());
                k.merge("stored" + stored.size(), 1, Integer::sum);
                var acked = first.get(p).outcome() == RequestRecord.Outcome.ACK
                        || (retries.containsKey(p) && retries.get(p).outcome() == RequestRecord.Outcome.ACK);
                if (stored.size() > 1) {
                    duplicates.add(p.kind() + " key " + p.key() + " stored " + stored.size() + "x: "
                            + stored.stream().map(e -> e.aggregateId() + "@" + e.seq()).toList());
                }
                if (acked && stored.isEmpty()) lost++;
                if (retries.containsKey(p) && retries.get(p).outcome() == RequestRecord.Outcome.REJECTED) {
                    k.merge("retryRejected409", 1, Integer::sum);
                }
            }
            report.metric("storedCopiesPerKind", perKind);
            report.metric("acknowledgedButNotStored", lost);
            if (!duplicates.isEmpty()) report.attach("duplicates", duplicates);

            Boolean correct = !duplicates.isEmpty() ? Boolean.FALSE : (ambiguous.isEmpty() ? null : Boolean.TRUE);
            report.observe("a retried command is stored once",
                    "exactly one stored event per idempotency key (key persisted with the event, G5 fix)",
                    "a command stored before its reply was lost is stored again by the retry (G5)",
                    duplicates.size() + " key(s) stored twice; " + ambiguous.size() + " of " + firstFailed.size()
                            + " failed first attempts were stored before the retry; per kind " + perKind,
                    correct, !duplicates.isEmpty(),
                    correct == null ? "No command was caught in the ambiguous window; tune S14_KILL_DELAY_MS." : null);

            // The other half of exactly-once: the retry of a command that was stored must tell the
            // client "done" (the replayed event), not "refused" (409) nor a second copy.
            int replayAcked = 0;
            var notAcked = new ArrayList<String>();
            for (var p : ambiguous) {
                var r = retries.get(p);
                if (r != null && r.outcome() == RequestRecord.Outcome.ACK) replayAcked++;
                else notAcked.add(p.kind() + " " + p.key() + ": " + (r == null ? "not retried" : r.outcome() + " " + r.kind()));
            }
            report.metric("storedThenRetriedAcked", replayAcked);
            if (!notAcked.isEmpty()) report.attach("storedThenRetriedNotAcked", notAcked);
            Boolean replayOk = ambiguous.isEmpty() ? null : replayAcked == ambiguous.size();
            report.observe("the retry of a stored command is acknowledged with the stored event",
                    "every retry of a command stored before the retry answers 2xx and stores nothing (G5 replay)",
                    "the retry is stored again, or refused with 409 by the aggregate (create with a client id)",
                    replayAcked + " of " + ambiguous.size() + " stored-then-retried commands acknowledged",
                    replayOk, Boolean.FALSE.equals(replayOk), null);
            report.observe("acknowledged commands are stored", "every 2xx has its event", lost + " missing",
                    lost == 0);
            report.observe("in-flight commands fail fast when their server dies",
                    "typed failure right away (G4 fix)", "fail only after the 30 s request timeout (G4)",
                    timedOut + " of " + firstFailed.size() + " failures took >= 25 s",
                    firstFailed.isEmpty() ? null : timedOut == 0, timedOut > 0, null);
        });
    }

    private RequestRecord send(Probe p, String region, boolean retry) {
        return switch (p.kind()) {
            case "create-auto-id" -> gateway.create(region, null, p.key(), retry ? "CINA" : null);
            case "create-client-id" -> gateway.create(region, p.orderId(), p.key(), retry ? "CINA" : null);
            default -> gateway.addItem(region, p.orderId(), "sku-retry", 1, p.key());
        };
    }
}
