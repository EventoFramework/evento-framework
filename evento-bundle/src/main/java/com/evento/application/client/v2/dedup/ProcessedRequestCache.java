package com.evento.application.client.v2.dedup;

import com.evento.transport.message.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound de-duplication cache for the exactly-once-effective RPC contract.
 *
 * <p>When a {@code Request} arrives, the bundle calls {@link #resolveOrClaim}
 * with the correlationId:
 *
 * <ul>
 *   <li>If the request has not been seen → returns {@link Outcome.Claimed}.
 *       The caller invokes the handler and reports the produced
 *       {@link Response} back via {@link #recordResponse}.</li>
 *   <li>If the request <em>has</em> been seen and a response was recorded →
 *       returns {@link Outcome.Replay} with that response. The caller re-sends
 *       it on the wire without re-running the handler.</li>
 *   <li>If the request is currently in flight (claimed but not yet recorded) →
 *       returns {@link Outcome.InFlight}. The caller can either wait or drop
 *       the duplicate; the most common policy is to drop, because the original
 *       will eventually reply.</li>
 * </ul>
 *
 * <p>Storage is bounded by a max-entries cap plus a TTL. The cache is keyed
 * by {@link UUID} so it scales with the recent request volume, not the total
 * lifetime traffic. It is safe to share across threads.
 */
public final class ProcessedRequestCache {

    private static final Logger log = LoggerFactory.getLogger(ProcessedRequestCache.class);

    public sealed interface Outcome permits Outcome.Claimed, Outcome.InFlight, Outcome.Replay {
        record Claimed() implements Outcome {}
        record InFlight() implements Outcome {}
        record Replay(Response response) implements Outcome {}
        Outcome CLAIMED = new Claimed();
        Outcome IN_FLIGHT = new InFlight();
    }

    private static final class Entry {
        final long claimedAtMs;
        volatile Response response;  // null while in-flight
        Entry(long claimedAtMs) { this.claimedAtMs = claimedAtMs; }
    }

    private final int maxEntries;
    private final long ttlMs;
    private final Map<UUID, Entry> entries;

    public ProcessedRequestCache(int maxEntries, Duration ttl) {
        this.maxEntries = maxEntries;
        this.ttlMs = ttl.toMillis();
        // Access-order LRU; eldest entry evicted when capacity is exceeded.
        this.entries = java.util.Collections.synchronizedMap(
                new LinkedHashMap<UUID, Entry>(maxEntries + 16, 0.75f, true) {
                    @Override protected boolean removeEldestEntry(Map.Entry<UUID, Entry> eldest) {
                        return size() > maxEntries;
                    }
                });
    }

    public ProcessedRequestCache() {
        this(10_000, Duration.ofMinutes(5));
    }

    public Outcome resolveOrClaim(UUID correlationId) {
        long now = System.currentTimeMillis();
        synchronized (entries) {
            var existing = entries.get(correlationId);
            if (existing != null) {
                if (now - existing.claimedAtMs > ttlMs) {
                    // Expired — treat as fresh.
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
                log.warn("event=record_response_unknown correlationId={}", correlationId);
                return;
            }
            entry.response = response;
        }
    }

    public void forget(UUID correlationId) {
        synchronized (entries) {
            entries.remove(correlationId);
        }
    }

    public int size() {
        synchronized (entries) { return entries.size(); }
    }
}
