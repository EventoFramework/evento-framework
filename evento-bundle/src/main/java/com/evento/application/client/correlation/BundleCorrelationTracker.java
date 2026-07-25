package com.evento.application.client.correlation;

import com.evento.transport.RequestTimeoutException;
import com.evento.transport.ShutdownInProgressException;
import com.evento.transport.message.Response;
import com.evento.transport.message.ResponseError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bundle-side analogue of the server's {@code CorrelationStore}. Each outbound
 * {@code Request} parks a {@link CompletableFuture<Response>} keyed by
 * {@code correlationId}; the matching inbound {@code Response} completes it.
 *
 * <p>An internal scheduler expires entries that miss their deadline so failed
 * Requests don't leak forever. {@link #shutdown} fails every still-pending
 * future with {@link ShutdownInProgressException} after the configured
 * deadline.
 */
public final class BundleCorrelationTracker {

    private static final Logger log = LoggerFactory.getLogger(BundleCorrelationTracker.class);

    private record Pending(String payloadType, long submittedAtMs, long timeoutMs,
                           CompletableFuture<Response> future) {
        boolean isExpired(long nowMs) {
            return timeoutMs > 0 && (nowMs - submittedAtMs) > timeoutMs;
        }
    }

    private final ConcurrentHashMap<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner;
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);

    public BundleCorrelationTracker(Duration cleanInterval) {
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "evento-bundle-correlation-cleaner");
            t.setDaemon(true);
            return t;
        });
        long intervalMs = Math.max(50L, cleanInterval.toMillis());
        cleaner.scheduleWithFixedDelay(this::expireOnce, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public BundleCorrelationTracker() {
        this(Duration.ofMillis(500));
    }

    /**
     * @param payloadType what is being sent, carried purely so an expiry can say
     *                    which message type ran out of time. Without it the
     *                    expiry log reports a count and nothing actionable.
     */
    public CompletableFuture<Response> track(UUID correlationId, String payloadType, Duration timeout) {
        if (shutdownInitiated.get()) {
            return CompletableFuture.failedFuture(
                    new ShutdownInProgressException("bundle correlation tracker shutting down"));
        }
        var future = new CompletableFuture<Response>();
        var entry = new Pending(payloadType == null ? "unknown" : payloadType, System.currentTimeMillis(),
                timeout == null ? 0L : timeout.toMillis(), future);
        var existing = pending.putIfAbsent(correlationId, entry);
        if (existing != null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("duplicate correlation id: " + correlationId));
        }
        future.whenComplete((r, t) -> pending.remove(correlationId));
        return future;
    }

    /**
     * Try to complete the future associated with {@code response.correlationId()}.
     * Returns true if a match was found.
     */
    public boolean complete(Response response) {
        var entry = pending.remove(response.correlationId());
        if (entry == null) return false;
        entry.future.complete(response);
        return true;
    }

    /**
     * Fail every pending request with {@code cause}. Used on a transport drop
     * so callers learn quickly instead of waiting out the per-request timeout.
     */
    public int failAll(Throwable cause) {
        int count = 0;
        for (var key : java.util.List.copyOf(pending.keySet())) {
            var entry = pending.remove(key);
            if (entry != null) {
                entry.future.completeExceptionally(cause);
                count++;
            }
        }
        return count;
    }

    public int pendingCount() {
        return pending.size();
    }

    private void expireOnce() {
        if (pending.isEmpty()) return;
        long now = System.currentTimeMillis();
        int expired = 0;
        var byType = new java.util.TreeMap<String, Integer>();
        for (var key : java.util.List.copyOf(pending.keySet())) {
            var entry = pending.get(key);
            if (entry == null || !entry.isExpired(now)) continue;
            if (!pending.remove(key, entry)) continue;
            var err = new ResponseError(RequestTimeoutException.class.getName(),
                    "request expired after " + (now - entry.submittedAtMs) + "ms (timeout="
                            + entry.timeoutMs + ")", null);
            entry.future.complete(Response.failure(key, err));
            byType.merge(entry.payloadType(), 1, Integer::sum);
            expired++;
        }
        if (expired > 0) {
            // Warn with the breakdown: "which message type is timing out" is the
            // first question asked when throughput drops, and a bare count cannot
            // answer it. `pending` alongside it shows whether the backlog is
            // draining or still growing.
            log.warn("event=bundle_correlation_expired count={} pending={} byType={}",
                    expired, pending.size(), byType);
        }
    }

    public void shutdown(Duration deadline) {
        if (!shutdownInitiated.compareAndSet(false, true)) return;
        long cutoffMs = System.currentTimeMillis() + deadline.toMillis();
        while (!pending.isEmpty() && System.currentTimeMillis() < cutoffMs) {
            try { Thread.sleep(50L); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        if (!pending.isEmpty()) {
            failAll(new ShutdownInProgressException("bundle shutting down"));
        }
        cleaner.shutdownNow();
        try { cleaner.awaitTermination(1, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
