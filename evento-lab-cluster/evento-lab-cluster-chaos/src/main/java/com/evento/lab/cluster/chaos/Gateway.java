package com.evento.lab.cluster.chaos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Talks to the region gateways through the location-aware LB (header
 * {@code X-Region}) and generates load: each "flow" creates an order, adds
 * items and optionally completes it, on virtual threads, recording every call
 * with its latency and outcome.
 *
 * <p>Order ids are {@code <prefix><region>-<n>}; the prefix scopes every
 * invariant check to the orders of one scenario run.
 */
public class Gateway {

    /**
     * @param ratePerMinutePerRegion  new order flows per minute per region
     * @param itemsPerOrder           items added after creation
     * @param completeRatio           fraction of flows that complete the order (0..1)
     * @param duration                how long new flows are started
     */
    public record LoadPlan(List<String> regions, double ratePerMinutePerRegion, int itemsPerOrder,
                           double completeRatio, Duration duration) {
    }

    private final String baseUrl;
    private final HttpClient http;
    private final Duration requestTimeout;
    private final ConcurrentLinkedQueue<RequestRecord> records = new ConcurrentLinkedQueue<>();
    private final AtomicInteger sequence = new AtomicInteger();

    public Gateway(String baseUrl) {
        this(baseUrl, Duration.ofSeconds(LabConfig.envInt("EVENTO_LAB_HTTP_TIMEOUT_S", 45)));
    }

    public Gateway(String baseUrl, Duration requestTimeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    public List<RequestRecord> records() {
        return List.copyOf(records);
    }

    public void clear() {
        records.clear();
    }

    public String newOrderId(String prefix, String region) {
        return prefix + region.toLowerCase().replace('_', '-') + "-" + sequence.incrementAndGet();
    }

    // ---- single calls ---------------------------------------------------------------------------

    public RequestRecord create(String region, String orderId, String idempotencyKey) {
        return create(region, orderId, idempotencyKey, null);
    }

    /** @param contextOverride lab-only hook to stamp another context on the event (S11). */
    public RequestRecord create(String region, String orderId, String idempotencyKey, String contextOverride) {
        var body = new LinkedHashMap<String, Object>();
        if (orderId != null) body.put("orderId", orderId);
        if (contextOverride != null) body.put("context", contextOverride);
        return call("create", region, orderId, "POST", "/orders", Json.write(body), idempotencyKey);
    }

    public RequestRecord addItem(String region, String orderId, String sku, int qty, String idempotencyKey) {
        return call("add_item", region, orderId, "POST", "/orders/" + orderId + "/items",
                Json.write(Map.of("sku", sku, "qty", qty)), idempotencyKey);
    }

    public RequestRecord complete(String region, String orderId, String idempotencyKey) {
        return call("complete", region, orderId, "POST", "/orders/" + orderId + "/complete", "{}", idempotencyKey);
    }

    public RequestRecord getRegional(String region, String orderId) {
        return call("get_regional", region, orderId, "GET", "/orders/" + orderId, null, null);
    }

    public RequestRecord getGlobal(String region, String orderId) {
        return call("get_global", region, orderId, "GET", "/orders/global/" + orderId, null, null);
    }

    /**
     * Set a world-wide price through a region's gateway: one {@code PriceChangedEvent}
     * with the literal context {@code ALL}. Recorded with op {@code set_price} and the
     * SKU in the order-id slot (not an order command: the order ledgers ignore it).
     */
    public RequestRecord setPrice(String region, String sku, long priceCents, String idempotencyKey) {
        return call("set_price", region, sku, "POST", "/catalog/prices",
                Json.write(Map.of("sku", sku, "priceCents", priceCents, "currency", "EUR")), idempotencyKey);
    }

    /** A price from one regional database ({@code db} = {@code eu}/{@code am}/{@code apac}), or the caller's cell. */
    public RequestRecord getPrice(String region, String sku, String db) {
        return call("get_price", region, sku, "GET", "/catalog/prices/" + sku + (db == null ? "" : "?db=" + db),
                null, null);
    }

    /** Raw GET through the LB with a region key, returning the body (or null). */
    public String getBody(String region, String path) {
        try {
            var req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("X-Region", region)
                    .GET().build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() < 300 ? resp.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private RequestRecord call(String op, String region, String orderId, String method, String path,
                               String body, String idempotencyKey) {
        long start = System.currentTimeMillis();
        long t0 = System.nanoTime();
        RequestRecord rec;
        try {
            var b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(requestTimeout)
                    .header("X-Region", region)
                    .header("Content-Type", "application/json");
            if (idempotencyKey != null) b.header("X-Idempotency-Key", idempotencyKey);
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            var resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            long ms = (System.nanoTime() - t0) / 1_000_000;
            var status = resp.statusCode();
            var json = Json.read(resp.body());
            var servedBy = resp.headers().firstValue("X-Lab-Upstream").orElse(null);
            var outcome = status < 300 ? RequestRecord.Outcome.ACK
                    : status == 409 ? RequestRecord.Outcome.REJECTED
                    : status == 404 ? RequestRecord.Outcome.NOT_FOUND
                    : RequestRecord.Outcome.ERROR;
            var kind = json.path("kind").asText(status < 300 ? "ok" : "http_" + status);
            var err = status < 300 ? null : truncate(json.path("error").asText(resp.body()));
            rec = new RequestRecord(start, ms, region, op, orderId, status, outcome, kind, err, servedBy, idempotencyKey);
        } catch (java.net.http.HttpTimeoutException e) {
            rec = new RequestRecord(start, (System.nanoTime() - t0) / 1_000_000, region, op, orderId, 0,
                    RequestRecord.Outcome.NO_RESPONSE, "client_timeout", truncate(e.toString()), null, idempotencyKey);
        } catch (Exception e) {
            rec = new RequestRecord(start, (System.nanoTime() - t0) / 1_000_000, region, op, orderId, 0,
                    RequestRecord.Outcome.NO_RESPONSE, "client_error", truncate(e.toString()), null, idempotencyKey);
        }
        records.add(rec);
        return rec;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    // ---- load -----------------------------------------------------------------------------------

    /**
     * Run one order flow: create, items, maybe complete. Stops at the first
     * failed step (a later step on an order whose creation is unknown would
     * only add noise).
     */
    public void flow(String region, String orderId, int items, boolean complete) {
        var create = create(region, orderId, UUID.randomUUID().toString());
        if (create.outcome() != RequestRecord.Outcome.ACK) return;
        for (int i = 0; i < items; i++) {
            var r = addItem(region, orderId, "sku-" + (i + 1), 1 + (i % 3), UUID.randomUUID().toString());
            if (r.outcome() != RequestRecord.Outcome.ACK) return;
        }
        if (complete) {
            complete(region, orderId, UUID.randomUUID().toString());
        }
    }

    /**
     * Start {@code plan.ratePerMinutePerRegion} flows per minute in every
     * region for {@code plan.duration}, then wait (bounded) for in-flight flows.
     * {@code onTick} is called about once a second with the elapsed time, so a
     * scenario can inject its fault mid-load.
     */
    public List<RequestRecord> runLoad(String prefix, LoadPlan plan, Consumer<Duration> onTick) {
        var started = Instant.now();
        var stop = new AtomicBoolean(false);
        var before = records.size();
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            var intervalMs = (long) Math.max(5, 60_000.0 / Math.max(0.01, plan.ratePerMinutePerRegion()));
            for (var region : plan.regions()) {
                exec.submit(() -> {
                    // de-phase the regions a little
                    Cluster.sleep(Duration.ofMillis(ThreadLocalRandom.current().nextLong(Math.min(intervalMs, 1000))));
                    while (!stop.get()) {
                        var orderId = newOrderId(prefix, region);
                        var complete = ThreadLocalRandom.current().nextDouble() < plan.completeRatio();
                        try {
                            exec.submit(() -> flow(region, orderId, plan.itemsPerOrder(), complete));
                        } catch (java.util.concurrent.RejectedExecutionException e) {
                            return;
                        }
                        Cluster.sleep(Duration.ofMillis(intervalMs));
                    }
                });
            }
            var ticker = Thread.ofVirtual().start(() -> {
                while (!stop.get()) {
                    try {
                        if (onTick != null) onTick.accept(Duration.between(started, Instant.now()));
                    } catch (RuntimeException e) {
                        System.out.println("[gateway] tick callback failed: " + e);
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });
            Cluster.sleep(plan.duration());
            stop.set(true);
            ticker.interrupt();
            exec.shutdown();
            // flows in flight may be stuck behind a 30 s command timeout
            exec.awaitTermination(requestTimeout.toSeconds() * (plan.itemsPerOrder() + 2L) + 30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        var all = records();
        return all.subList(Math.min(before, all.size()), all.size());
    }

    public static List<RequestRecord> commands(Collection<RequestRecord> rs) {
        var out = new ArrayList<RequestRecord>();
        for (var r : rs) if (r.isCommand()) out.add(r);
        return out;
    }
}
