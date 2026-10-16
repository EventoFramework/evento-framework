package com.evento.server.bus.correlation;

import com.evento.transport.message.Response;
import com.evento.transport.message.ResponseError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Broker-side dedup for forwarded {@code Request}s. Implements the broker's
 * half of exactly-once-effective RPC: if the same {@code correlationId}
 * arrives twice within the cache window, the duplicate is short-circuited:
 *
 * <ul>
 *   <li>Either the original response was already produced and forwarded back to
 *       the originator → the duplicate gets the cached response (
 *       {@link Outcome.Replay}).</li>
 *   <li>Or the original is still in flight → drop the duplicate (
 *       {@link Outcome.InFlight}); the response will arrive once and the
 *       broker will route it to whichever originator is currently listening.</li>
 *   <li>Otherwise this is a fresh request → claim a slot (
 *       {@link Outcome.Claimed}). The caller routes normally and reports the
 *       eventual response via {@link #recordResponse}.</li>
 * </ul>
 *
 * <p>Only requests with side effects belong here. Queries and other idempotent
 * reads are routed "straight" by the broker and never claim a slot: a duplicate
 * of a read is just answered again, and retaining read results (which can be
 * the largest payloads on the bus) bought nothing but heap.
 *
 * <p>The cache is access-order LRU bounded twice: by entry count and by the
 * total bytes of the responses it retains. A response larger than
 * {@code maxResponseBytes} is not retained at all; its slot remembers that the
 * request completed, and a late duplicate is told so (an error naming
 * {@link DuplicateRequestException}) instead of being executed a second time. A
 * TTL prunes stale entries on read. One lock over the map: the path is short and
 * the lock is held only over the cache lookup, not the network operation.
 *
 * <p>Defaults: 50 000 entries, 5 min TTL, 128 MiB of retained responses, 1 MiB per
 * response. Tune via constructor.
 */
public final class ForwardingDedupCache {

    private static final Logger log = LoggerFactory.getLogger(ForwardingDedupCache.class);

    /** Default bound on the bytes of all retained responses. */
    public static final long DEFAULT_MAX_RETAINED_BYTES = 128L * 1024 * 1024;
    /** Default bound on one retained response; larger ones are remembered as completed only. */
    public static final int DEFAULT_MAX_RESPONSE_BYTES = 1024 * 1024;

    public sealed interface Outcome permits Outcome.Claimed, Outcome.InFlight, Outcome.Replay {
        record Claimed() implements Outcome {}
        record InFlight() implements Outcome {}
        record Replay(Response response) implements Outcome {}
        Outcome CLAIMED = new Claimed();
        Outcome IN_FLIGHT = new InFlight();
    }

    /**
     * Named in the error a duplicate of a completed request gets when the original
     * response was too large to retain: the request was executed once and answered.
     */
    public static final class DuplicateRequestException extends IllegalStateException {
        public DuplicateRequestException(String message) { super(message); }
    }

    private static final class Entry {
        final long claimedAtMs;
        Response response;
        boolean completedNotRetained;
        long bytes;
        Entry(long claimedAtMs) { this.claimedAtMs = claimedAtMs; }
    }

    private final long ttlMs;
    private final long maxRetainedBytes;
    private final int maxResponseBytes;
    private final LinkedHashMap<UUID, Entry> entries;
    private long retainedBytes;

    public ForwardingDedupCache(int maxEntries, Duration ttl) {
        this(maxEntries, ttl, DEFAULT_MAX_RETAINED_BYTES, DEFAULT_MAX_RESPONSE_BYTES);
    }

    public ForwardingDedupCache(int maxEntries, Duration ttl, long maxRetainedBytes, int maxResponseBytes) {
        this.ttlMs = ttl.toMillis();
        this.maxRetainedBytes = Math.max(0L, maxRetainedBytes);
        this.maxResponseBytes = Math.max(0, maxResponseBytes);
        this.entries = new LinkedHashMap<>(maxEntries + 16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<UUID, Entry> eldest) {
                if (size() > maxEntries) {
                    retainedBytes -= eldest.getValue().bytes;
                    return true;
                }
                return false;
            }
        };
    }

    public ForwardingDedupCache() {
        this(50_000, Duration.ofMinutes(5));
    }

    public Outcome resolveOrClaim(UUID correlationId) {
        long now = System.currentTimeMillis();
        synchronized (entries) {
            var existing = entries.get(correlationId);
            if (existing != null) {
                if (now - existing.claimedAtMs > ttlMs) {
                    retainedBytes -= existing.bytes;
                    entries.put(correlationId, new Entry(now));
                    return Outcome.CLAIMED;
                }
                if (existing.response != null) {
                    return new Outcome.Replay(existing.response);
                }
                if (existing.completedNotRetained) {
                    return new Outcome.Replay(Response.failure(correlationId, new ResponseError(
                            DuplicateRequestException.class.getName(),
                            "duplicate of request " + correlationId + ", which was executed once and answered;"
                                    + " its response was too large to keep for replay",
                            null)));
                }
                return Outcome.IN_FLIGHT;
            }
            entries.put(correlationId, new Entry(now));
            return Outcome.CLAIMED;
        }
    }

    public void recordResponse(UUID correlationId, Response response) {
        long size = sizeOf(response);
        synchronized (entries) {
            var entry = entries.get(correlationId);
            if (entry == null) {
                log.debug("event=dedup_record_unknown correlationId={}", correlationId);
                return;
            }
            retainedBytes -= entry.bytes;
            entry.bytes = 0;
            if (size > maxResponseBytes) {
                entry.response = null;
                entry.completedNotRetained = true;
                return;
            }
            entry.response = response;
            entry.bytes = size;
            retainedBytes += size;
            evictToByteBudget(correlationId);
        }
    }

    /** Drop the slot for a correlation (e.g. when the originator disconnects before reply). */
    public void invalidate(UUID correlationId) {
        synchronized (entries) {
            var removed = entries.remove(correlationId);
            if (removed != null) retainedBytes -= removed.bytes;
        }
    }

    public int size() {
        synchronized (entries) { return entries.size(); }
    }

    /** Bytes of the responses currently retained for replay. */
    public long retainedBytes() {
        synchronized (entries) { return retainedBytes; }
    }

    /**
     * Least recently used first, never the entry just recorded. An evicted entry keeps
     * its slot (it still answers a duplicate, with "completed, not retained"): only the
     * response bytes go, the count bound removes slots. Caller holds the lock.
     */
    private void evictToByteBudget(UUID keep) {
        if (retainedBytes <= maxRetainedBytes) return;
        int evicted = 0;
        for (var e : entries.entrySet()) {
            if (retainedBytes <= maxRetainedBytes) break;
            var entry = e.getValue();
            if (e.getKey().equals(keep) || entry.bytes == 0) continue;
            retainedBytes -= entry.bytes;
            entry.bytes = 0;
            entry.response = null;
            entry.completedNotRetained = true;
            evicted++;
        }
        if (evicted > 0) {
            log.debug("event=dedup_evicted_for_bytes count={} retainedBytes={}", evicted, retainedBytes);
        }
    }

    private static long sizeOf(Response response) {
        if (response == null) return 0;
        var payload = response.payload();
        return 64L + (payload == null ? 0 : payload.length);
    }
}
