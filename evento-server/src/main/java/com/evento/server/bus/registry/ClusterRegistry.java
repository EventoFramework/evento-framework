package com.evento.server.bus.registry;

import com.evento.server.bus.NodeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Maps payload types (commands / queries / event topics) to the set of
 * {@link NodeAddress}es that can serve them. Replaces v1 {@code MessageBus}'s
 * {@code handlers} map plus the random/round-robin selection logic embedded in
 * {@code peekMessageHandlerAddress()}.
 *
 * <p>Pick strategies live behind a small enum so routing policy can evolve
 * without touching call sites. Read paths return snapshots.
 *
 * <p>Thread-safety: per-payload sets are built on {@code ConcurrentHashMap.newKeySet()},
 * so concurrent register/remove on the same key is safe.
 */
public final class ClusterRegistry {

    private static final Logger log = LoggerFactory.getLogger(ClusterRegistry.class);

    /**
     * How {@link #pick} chooses among the available handlers of a payload type.
     *
     * <ul>
     *   <li>{@code RANDOM} — uniform over every available handler (the default).</li>
     *   <li>{@code FIRST} — the first in iteration order; deterministic, for tests.</li>
     *   <li>{@code LOCALITY_PREFERRED} — uniform over the available handlers whose
     *       zone equals the zone of the bundle that originated the request; when
     *       the originator has no zone, or no handler in that zone is available,
     *       uniform over every available handler. It never refuses a request that
     *       {@code RANDOM} would have routed: locality is a preference, not a
     *       partition.</li>
     * </ul>
     */
    public enum PickStrategy { RANDOM, FIRST, LOCALITY_PREFERRED }

    /** payloadType → set of node addresses that handle it. */
    private final Map<String, Set<NodeAddress>> handlers = new ConcurrentHashMap<>();

    /**
     * instanceId → zone the instance advertised in its Hello ({@code zone:<ZONE>}), tagged
     * with the connection token of the session that advertised it. Keyed by instanceId
     * because {@link NodeAddress} equality is by instanceId; the token lets the teardown of
     * one session forget only its own zone, never the one a newer session of the same
     * instance has just recorded.
     */
    private record ZoneEntry(String zone, String connectionToken) {}

    private final Map<String, ZoneEntry> zoneByInstance = new ConcurrentHashMap<>();

    private final ConnectionRegistry connectionRegistry;
    private final PickStrategy defaultStrategy;

    public ClusterRegistry(ConnectionRegistry connectionRegistry) {
        this(connectionRegistry, PickStrategy.RANDOM);
    }

    public ClusterRegistry(ConnectionRegistry connectionRegistry, PickStrategy defaultStrategy) {
        this.connectionRegistry = connectionRegistry;
        this.defaultStrategy = defaultStrategy == null ? PickStrategy.RANDOM : defaultStrategy;
    }

    /** The strategy {@link #pick(String)} and {@link #pickFor(String, NodeAddress)} use. */
    public PickStrategy defaultStrategy() {
        return defaultStrategy;
    }

    /**
     * Record the zone {@code node} advertised; a null or blank zone forgets it.
     * Called on every Hello, so a reconnect that changes (or drops) its zone
     * takes effect on the next pick.
     */
    public void setZone(NodeAddress node, String zone) {
        setZone(node, null, zone);
    }

    /**
     * Record the zone the session {@code connectionToken} of {@code node} advertised; a null
     * or blank zone forgets it. The bus calls this once the session is registered, so a
     * disconnect of an older session of the same instance can no longer erase it
     * ({@link #forgetZone(NodeAddress, String)} checks the token).
     */
    public void setZone(NodeAddress node, String connectionToken, String zone) {
        if (node == null || node.instanceId() == null) return;
        if (zone == null || zone.isBlank()) {
            zoneByInstance.remove(node.instanceId());
        } else {
            zoneByInstance.put(node.instanceId(), new ZoneEntry(zone.trim(), connectionToken));
        }
    }

    /** Forget the zone of {@code node}, whichever session recorded it. */
    public void forgetZone(NodeAddress node) {
        if (node == null || node.instanceId() == null) return;
        zoneByInstance.remove(node.instanceId());
    }

    /**
     * Forget the zone of {@code node} only if the session {@code connectionToken} recorded it:
     * a newer session of the same instance that has already advertised its zone keeps it.
     *
     * @return whether an entry was removed
     */
    public boolean forgetZone(NodeAddress node, String connectionToken) {
        if (node == null || node.instanceId() == null) return false;
        var removed = new boolean[1];
        zoneByInstance.computeIfPresent(node.instanceId(), (id, entry) -> {
            if (java.util.Objects.equals(entry.connectionToken(), connectionToken)) {
                removed[0] = true;
                return null;
            }
            return entry;
        });
        return removed[0];
    }

    /** The zone {@code node} advertised, if any. */
    public Optional<String> zoneOf(NodeAddress node) {
        if (node == null || node.instanceId() == null) return Optional.empty();
        return Optional.ofNullable(zoneOfInstance(node.instanceId()));
    }

    private String zoneOfInstance(String instanceId) {
        var entry = zoneByInstance.get(instanceId);
        return entry == null ? null : entry.zone();
    }

    /** Snapshot of instanceId → zone for every node that advertised one. */
    public Map<String, String> zones() {
        var snapshot = new java.util.HashMap<String, String>();
        zoneByInstance.forEach((id, entry) -> snapshot.put(id, entry.zone()));
        return Map.copyOf(snapshot);
    }

    /**
     * Bind {@code node} to {@code payloadType}. Inside {@code compute}, atomic with
     * {@link #removeNode}: the set is created and the node added in one step, so the
     * compaction of another node's removal can no longer drop a set between its creation
     * and the add (two bundles registering at once lost one registration).
     */
    public void registerHandler(NodeAddress node, String payloadType) {
        handlers.compute(payloadType, (k, nodes) -> {
            var set = nodes == null ? ConcurrentHashMap.<NodeAddress>newKeySet() : nodes;
            set.add(node);
            return set;
        });
    }

    public void registerHandlers(NodeAddress node, Collection<String> payloadTypes) {
        for (String type : payloadTypes) {
            registerHandler(node, type);
        }
        log.info("event=handlers_registered node={} count={}", node.instanceId(), payloadTypes.size());
    }

    /**
     * Remove a node from every payload binding.
     *
     * @return the set of payload types whose handler set became <em>empty</em>
     *         after removal — callers use this to evict matching local-handler
     *         registrations (e.g. {@code BusLifecycle.localHandlers}).
     */
    public Set<String> removeNode(NodeAddress node) {
        Set<String> nowEmpty = ConcurrentHashMap.newKeySet();
        // Per payload type and inside computeIfPresent, so a registration of the same type
        // runs wholly before or after: an entry is dropped only when this removal empties it.
        for (String pt : handlers.keySet()) {
            handlers.computeIfPresent(pt, (k, nodes) -> {
                if (!nodes.remove(node)) return nodes;
                if (!nodes.isEmpty()) return nodes;
                nowEmpty.add(k);
                return null;
            });
        }
        log.info("event=node_removed node={} emptyTypes={}", node.instanceId(), nowEmpty.size());
        return Collections.unmodifiableSet(nowEmpty);
    }

    /**
     * All nodes (registered) bound to {@code payloadType}, filtered to those that
     * are currently AVAILABLE (i.e. enabled in {@link ConnectionRegistry}).
     */
    public Set<NodeAddress> availableNodesFor(String payloadType) {
        Set<NodeAddress> all = handlers.getOrDefault(payloadType, Set.of());
        if (all.isEmpty()) return Set.of();
        return all.stream()
                .filter(connectionRegistry::isAvailable)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Pick one available node for {@code payloadType}, using the supplied strategy
     * and no originator (so {@code LOCALITY_PREFERRED} behaves as {@code RANDOM}).
     */
    public Optional<NodeAddress> pick(String payloadType, PickStrategy strategy) {
        return pick(payloadType, strategy, null);
    }

    /**
     * Pick one available node for {@code payloadType}. {@code originator} is the
     * bundle whose request is being routed; only {@code LOCALITY_PREFERRED} looks
     * at it, and a null originator (a server-initiated request) means "no zone".
     */
    public Optional<NodeAddress> pick(String payloadType, PickStrategy strategy, NodeAddress originator) {
        Set<NodeAddress> candidates = availableNodesFor(payloadType);
        if (candidates.isEmpty()) return Optional.empty();
        return switch (strategy == null ? PickStrategy.RANDOM : strategy) {
            case RANDOM -> randomOf(candidates);
            case FIRST -> candidates.stream().findFirst();
            case LOCALITY_PREFERRED -> {
                String zone = zoneOf(originator).orElse(null);
                if (zone == null) yield randomOf(candidates);
                var local = candidates.stream()
                        .filter(n -> zone.equals(zoneOfInstance(n.instanceId())))
                        .toList();
                yield local.isEmpty() ? randomOf(candidates) : randomOf(local);
            }
        };
    }

    /** Pick with the configured default strategy and no originator. */
    public Optional<NodeAddress> pick(String payloadType) {
        return pick(payloadType, defaultStrategy, null);
    }

    /** Pick with the configured default strategy on behalf of {@code originator}. */
    public Optional<NodeAddress> pickFor(String payloadType, NodeAddress originator) {
        return pick(payloadType, defaultStrategy, originator);
    }

    private static Optional<NodeAddress> randomOf(Collection<NodeAddress> candidates) {
        if (candidates.isEmpty()) return Optional.empty();
        int idx = ThreadLocalRandom.current().nextInt(candidates.size());
        int i = 0;
        for (NodeAddress addr : candidates) {
            if (i++ == idx) return Optional.of(addr);
        }
        return Optional.empty();
    }

    public Set<String> knownPayloadTypes() {
        return Collections.unmodifiableSet(Set.copyOf(handlers.keySet()));
    }

    public int handlerCount(String payloadType) {
        return handlers.getOrDefault(payloadType, Set.of()).size();
    }
}
