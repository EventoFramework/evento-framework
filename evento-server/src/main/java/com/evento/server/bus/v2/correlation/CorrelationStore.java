package com.evento.server.bus.v2.correlation;

import com.evento.server.bus.NodeAddress;
import com.evento.transport.ShutdownInProgressException;
import com.evento.transport.message.Response;
import com.evento.transport.message.ResponseError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Tracks in-flight server-side correlations: a server-initiated request awaits
 * a {@link Response} keyed by {@code correlationId}.
 *
 * <p>Replaces v1's {@code correlations} map plus the standalone scheduler that
 * polled {@code request.checkExpired()}. The store owns its scheduler and shuts
 * down deterministically.
 *
 * <p>Shutdown contract: {@link #shutdown(Duration)} blocks at most the supplied
 * deadline waiting for outstanding futures to complete naturally; once the
 * deadline passes, any remaining correlation is cancelled with
 * {@link ShutdownInProgressException}. Fixes v1's unbounded
 * {@code disableDelayMillis × maxDisableAttempts} wait (default 150s).
 *
 * <p>Thread-safety: all mutation goes through {@link ConcurrentHashMap}; the
 * scheduler thread and submitting threads never share locks.
 */
public final class CorrelationStore {

    private static final Logger log = LoggerFactory.getLogger(CorrelationStore.class);

    private final ConcurrentHashMap<UUID, PendingCorrelation> correlations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner;
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);

    public CorrelationStore(Duration cleanInterval) {
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "evento-correlation-cleaner");
            t.setDaemon(true);
            return t;
        });
        long intervalMs = Math.max(50L, cleanInterval.toMillis());
        cleaner.scheduleWithFixedDelay(this::expireOnce, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public CorrelationStore() {
        this(Duration.ofSeconds(1));
    }

    /**
     * Submit a new correlation. The returned future completes when the matching
     * {@link Response} arrives via {@link #complete}, when the timeout expires,
     * or when the store is shut down.
     */
    public CompletableFuture<Response> submit(NodeAddress from,
                                              NodeAddress to,
                                              UUID correlationId,
                                              String payloadType,
                                              long timeoutMs) {
        if (shutdownInitiated.get()) {
            return CompletableFuture.failedFuture(
                    new ShutdownInProgressException("correlation store shutting down"));
        }
        var future = new CompletableFuture<Response>();
        var pending = new PendingCorrelation(correlationId, from, to, payloadType,
                System.currentTimeMillis(), timeoutMs, future);
        var previous = correlations.putIfAbsent(correlationId, pending);
        if (previous != null) {
            // Duplicate correlation id — vanishingly unlikely with UUIDs but defensive.
            return CompletableFuture.failedFuture(
                    new IllegalStateException("duplicate correlation id: " + correlationId));
        }
        future.whenComplete((r, t) -> correlations.remove(correlationId));
        return future;
    }

    /**
     * Resolve a pending correlation with the inbound {@link Response}. Returns
     * true if a matching pending entry was found and completed.
     */
    public boolean complete(Response response) {
        var pending = correlations.remove(response.correlationId());
        if (pending == null) {
            log.debug("event=orphan_response correlationId={}", response.correlationId());
            return false;
        }
        pending.future().complete(response);
        return true;
    }

    /**
     * Fail every pending correlation that matches {@code predicate}. Useful when
     * a connection drops: we fail every correlation routed to that node.
     */
    public int failMatching(Function<PendingCorrelation, Boolean> predicate, Throwable cause) {
        int count = 0;
        for (var entry : correlations.entrySet()) {
            var p = entry.getValue();
            if (predicate.apply(p) && correlations.remove(entry.getKey(), p)) {
                p.future().completeExceptionally(cause);
                count++;
            }
        }
        return count;
    }

    public int pendingCount() {
        return correlations.size();
    }

    private void expireOnce() {
        if (correlations.isEmpty()) return;
        long now = System.currentTimeMillis();
        int expired = 0;
        for (var entry : correlations.entrySet()) {
            var p = entry.getValue();
            if (!p.isExpired(now)) continue;
            if (!correlations.remove(entry.getKey(), p)) continue;
            var error = new ResponseError("com.evento.transport.RequestTimeoutException",
                    "request expired after " + (now - p.createdAt()) + "ms (timeout=" + p.timeoutMs() + ")",
                    null);
            p.future().complete(Response.failure(p.correlationId(), error));
            expired++;
        }
        if (expired > 0) {
            log.info("event=correlations_expired count={} pending={}", expired, correlations.size());
        }
    }

    /**
     * Wait up to {@code deadline} for pending correlations to drain naturally,
     * then cancel any remaining with {@link ShutdownInProgressException}.
     * Idempotent.
     */
    public void shutdown(Duration deadline) {
        if (!shutdownInitiated.compareAndSet(false, true)) {
            return;
        }
        Instant cutoff = Instant.now().plus(deadline);
        log.info("event=shutdown_started pending={} deadline_ms={}",
                correlations.size(), deadline.toMillis());
        while (!correlations.isEmpty() && Instant.now().isBefore(cutoff)) {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!correlations.isEmpty()) {
            int remaining = correlations.size();
            log.warn("event=shutdown_force_cancel remaining={}", remaining);
            var cancelException = new ShutdownInProgressException("server shutting down");
            for (var entry : correlations.entrySet()) {
                var p = correlations.remove(entry.getKey());
                if (p != null) {
                    p.future().completeExceptionally(cancelException);
                }
            }
        }
        cleaner.shutdownNow();
        try {
            cleaner.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("event=shutdown_complete");
    }
}
