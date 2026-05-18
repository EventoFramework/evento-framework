package com.evento.server.bus.v2.router;

import com.evento.server.bus.NodeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks where to send each {@link com.evento.transport.message.Response} back
 * when the server is forwarding a {@link com.evento.transport.message.Request}
 * between two bundles.
 *
 * <p>Differs from {@link com.evento.server.bus.v2.correlation.CorrelationStore}
 * because the server has nothing to "await" — it just relays the response. The
 * table records the originator address keyed by the same {@code correlationId}
 * that travels end-to-end with the request.
 *
 * <p>Stale entries (where the originator has disconnected before the response
 * arrives, or where the destination handler never replied) are pruned by the
 * caller via {@link #removeOlderThan(long)}.
 */
public final class ForwardingTable {

    private static final Logger log = LoggerFactory.getLogger(ForwardingTable.class);

    public record Entry(NodeAddress originator, NodeAddress destination,
                        String payloadType, long submittedAtMs) {}

    private final Map<UUID, Entry> table = new ConcurrentHashMap<>();

    /**
     * Remember that {@code correlationId} originated at {@code from} and is on its
     * way to {@code to}. Returns true if added, false if the id was already known
     * (which would indicate a UUID collision or a re-submit).
     */
    public boolean track(UUID correlationId, NodeAddress originator,
                         NodeAddress destination, String payloadType) {
        var entry = new Entry(originator, destination, payloadType, System.currentTimeMillis());
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
     * Drop entries older than {@code cutoffMs} epoch millis. Returns count removed.
     * Used by a periodic cleaner to bound memory when originators die mid-call.
     */
    public int removeOlderThan(long cutoffMs) {
        int[] count = {0};
        table.entrySet().removeIf(e -> {
            if (e.getValue().submittedAtMs() < cutoffMs) {
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
     * Drop every entry whose originator or destination matches {@code address}.
     * Called on disconnect to free the forwarding slot — the caller is responsible
     * for delivering a failure Response to surviving counterparties if needed.
     */
    public int removeInvolving(NodeAddress address) {
        int[] count = {0};
        table.entrySet().removeIf(e -> {
            if (e.getValue().originator().equals(address)
                    || e.getValue().destination().equals(address)) {
                count[0]++;
                return true;
            }
            return false;
        });
        return count[0];
    }

    public int size() {
        return table.size();
    }

    public Instant oldestEntryInstant() {
        long min = table.values().stream()
                .mapToLong(Entry::submittedAtMs)
                .min()
                .orElse(System.currentTimeMillis());
        return Instant.ofEpochMilli(min);
    }
}
