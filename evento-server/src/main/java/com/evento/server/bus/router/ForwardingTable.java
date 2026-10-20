package com.evento.server.bus.router;

import com.evento.server.bus.NodeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks where to send each {@link com.evento.transport.message.Response} back
 * when the server is forwarding a {@link com.evento.transport.message.Request}
 * between two bundles.
 *
 * <p>Differs from {@link com.evento.server.bus.correlation.CorrelationStore}
 * because the server has nothing to "await" — it just relays the response. The
 * table records the originator address keyed by the same {@code correlationId}
 * that travels end-to-end with the request.
 *
 * <p><b>Streams (2.6).</b> A request that asked for a streamed reply keeps its entry
 * for the whole stream: every frame is relayed by looking the entry up, not resolving
 * it, and refreshes its activity; the entry goes with the last frame, a cancel, or a
 * reply that ended it. A stream lives as long as it moves, so it is pruned by
 * inactivity, never by age.
 *
 * <p>Stale entries (where the originator has disconnected before the response
 * arrives, or where the destination handler never replied) are pruned by the
 * caller via {@link #removeOlderThan(long)}.
 */
public final class ForwardingTable {

    private static final Logger log = LoggerFactory.getLogger(ForwardingTable.class);

    /**
     * @param submittedAtMs  when the request was forwarded
     * @param streamed       whether the caller asked for a streamed reply
     * @param lastActivityMs the last frame relayed for it (= {@code submittedAtMs} until then)
     * @param receivedAtNanos {@link System#nanoTime()} when the request reached this broker, for
     *                        the request timer
     */
    public record Entry(UUID correlationId, NodeAddress originator, NodeAddress destination,
                        String payloadType, long submittedAtMs, boolean streamed, long lastActivityMs,
                        long receivedAtNanos) {

        public Entry(UUID correlationId, NodeAddress originator, NodeAddress destination,
                     String payloadType, long submittedAtMs) {
            this(correlationId, originator, destination, payloadType, submittedAtMs, false, submittedAtMs,
                    System.nanoTime());
        }

        Entry touched(long nowMs) {
            return new Entry(correlationId, originator, destination, payloadType, submittedAtMs, streamed, nowMs,
                    receivedAtNanos);
        }
    }

    private final Map<UUID, Entry> table = new ConcurrentHashMap<>();

    /**
     * Remember that {@code correlationId} originated at {@code from} and is on its
     * way to {@code to}. Returns true if added, false if the id was already known
     * (which would indicate a UUID collision or a re-submit).
     */
    public boolean track(UUID correlationId, NodeAddress originator,
                         NodeAddress destination, String payloadType) {
        return track(correlationId, originator, destination, payloadType, false);
    }

    /** {@link #track(UUID, NodeAddress, NodeAddress, String)} for a request that asked for a streamed reply. */
    public boolean track(UUID correlationId, NodeAddress originator,
                         NodeAddress destination, String payloadType, boolean streamed) {
        return track(correlationId, originator, destination, payloadType, streamed, System.nanoTime());
    }

    /** {@link #track(UUID, NodeAddress, NodeAddress, String, boolean)} with when the request arrived. */
    public boolean track(UUID correlationId, NodeAddress originator, NodeAddress destination,
                         String payloadType, boolean streamed, long receivedAtNanos) {
        long now = System.currentTimeMillis();
        var entry = new Entry(correlationId, originator, destination, payloadType, now, streamed, now,
                receivedAtNanos);
        return table.putIfAbsent(correlationId, entry) == null;
    }

    /**
     * Look up the originator for an inbound response and remove the entry
     * atomically. Returns empty if no entry was found (orphan response).
     */
    public Optional<Entry> resolve(UUID correlationId) {
        return Optional.ofNullable(table.remove(correlationId));
    }

    /**
     * The entry of a stream that is still running, refreshed: what a frame of the stream
     * is relayed by. Empty when unknown (ended, cancelled, pruned).
     */
    public Optional<Entry> touch(UUID correlationId) {
        long now = System.currentTimeMillis();
        return Optional.ofNullable(table.computeIfPresent(correlationId, (k, e) -> e.touched(now)));
    }

    /**
     * Drop entries older than {@code cutoffMs} epoch millis — for a stream, idle since
     * then. Returns count removed. Used by a periodic cleaner to bound memory when
     * originators die mid-call.
     */
    public int removeOlderThan(long cutoffMs) {
        int[] count = {0};
        table.entrySet().removeIf(e -> {
            var entry = e.getValue();
            long reference = entry.streamed() ? entry.lastActivityMs() : entry.submittedAtMs();
            if (reference < cutoffMs) {
                count[0]++;
                return true;
            }
            return false;
        });
        if (count[0] > 0) {
            log.info("event=forwarding_table_pruned count={} remaining={}", count[0], table.size());
        }
        return count[0];
    }

    /**
     * Atomically remove and return every entry whose originator or destination
     * matches {@code address}. Called on disconnect so the caller can notify the
     * surviving counterparty (typically with a failure {@code Response} when the
     * destination is the one that just left).
     */
    public List<Entry> drainInvolving(NodeAddress address) {
        var drained = new ArrayList<Entry>();
        for (var key : List.copyOf(table.keySet())) {
            var entry = table.get(key);
            if (entry == null) continue;
            if (entry.originator().equals(address) || entry.destination().equals(address)) {
                if (table.remove(key, entry)) {
                    drained.add(entry);
                }
            }
        }
        return drained;
    }

    /**
     * Atomically remove and return every entry where {@code address} is the
     * <em>destination</em> (handler peer). Used on handler-disconnect to surface
     * failures to originators while leaving originator-side entries alive for
     * reconnect delivery.
     */
    public List<Entry> drainByDestination(NodeAddress address) {
        var drained = new ArrayList<Entry>();
        for (var key : List.copyOf(table.keySet())) {
            var entry = table.get(key);
            if (entry == null) continue;
            if (entry.destination().equals(address)) {
                if (table.remove(key, entry)) {
                    drained.add(entry);
                }
            }
        }
        return drained;
    }

    /**
     * Atomically remove and return the streams {@code address} was receiving. A stream
     * cannot resume on another session (its frames would be duplicated or lost), so when
     * its caller goes away the handler is told to stop instead of waiting for credit.
     */
    public List<Entry> drainStreamsByOriginator(NodeAddress address) {
        var drained = new ArrayList<Entry>();
        for (var key : List.copyOf(table.keySet())) {
            var entry = table.get(key);
            if (entry == null || !entry.streamed()) continue;
            if (entry.originator().equals(address) && table.remove(key, entry)) {
                drained.add(entry);
            }
        }
        return drained;
    }

    /**
     * Convenience: drop entries involving {@code address} without returning them.
     */
    public int removeInvolving(NodeAddress address) {
        return drainInvolving(address).size();
    }

    public int size() {
        return table.size();
    }

    /** Streams currently relayed. */
    public int streamCount() {
        int n = 0;
        for (var entry : table.values()) {
            if (entry.streamed()) n++;
        }
        return n;
    }

    public Instant oldestEntryInstant() {
        long min = table.values().stream()
                .mapToLong(Entry::submittedAtMs)
                .min()
                .orElse(System.currentTimeMillis());
        return Instant.ofEpochMilli(min);
    }
}
