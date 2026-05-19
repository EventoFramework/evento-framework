package com.evento.server.bus.v2.correlation;

import com.evento.transport.message.Response;
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
 * <p>The cache is access-order LRU with a hard size cap, and a TTL prunes
 * stale entries on read. Tuned for low contention via a single lock on the
 * underlying map — the path is short and the lock is held only over the cache
 * lookup, not the network operation.
 *
 * <p>Defaults: 50 000 entries, 5 min TTL. Tune via constructor.
 */
public final class ForwardingDedupCache {

    private static final Logger log = LoggerFactory.getLogger(ForwardingDedupCache.class);

    public sealed interface Outcome permits Outcome.Claimed, Outcome.InFlight, Outcome.Replay {
        record Claimed() implements Outcome {}
        record InFlight() implements Outcome {}
        record Replay(Response response) implements Outcome {}
        Outcome CLAIMED = new Claimed();
        Outcome IN_FLIGHT = new InFlight();
    }

    private static final class Entry {
        final long claimedAtMs;
        volatile Response response;
        Entry(long claimedAtMs) { this.claimedAtMs = claimedAtMs; }
    }

    private final long ttlMs;
    private final Map<UUID, Entry> entries;

    public ForwardingDedupCache(int maxEntries, Duration ttl) {
        this.ttlMs = ttl.toMillis();
        this.entries = java.util.Collections.synchronizedMap(
                new LinkedHashMap<UUID, Entry>(maxEntries + 16, 0.75f, true) {
                    @Override protected boolean removeEldestEntry(Map.Entry<UUID, Entry> eldest) {
                        return size() > maxEntries;
                    }
                });
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
                    entries.put(correlationId, new Entry(now));
                    return Outcome.CLAIMED;
                }
                if (existing.response != null) {
                    return new Outcome.Replay(existing.response);
                }
                return Outcome.IN_FLIGHT;
            }
            entries.put(correlationId, new Entry(now));
            return Outcome.CLAIMED;
        }
    }

    public void recordResponse(UUID correlationId, Response response) {
        synchronized (entries) {
            var entry = entries.get(correlationId);
            if (entry == null) {
                log.debug("event=dedup_record_unknown correlationId={}", correlationId);
                return;
            }
            entry.response = response;
        }
    }

    /** Drop the slot for a correlation (e.g. when the originator disconnects before reply). */
    public void invalidate(UUID correlationId) {
        synchronized (entries) {
            entries.remove(correlationId);
        }
    }

    public int size() {
        synchronized (entries) { return entries.size(); }
    }
}
