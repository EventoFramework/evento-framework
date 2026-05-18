package com.evento.server.bus.v2.registry;

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

    public enum PickStrategy { RANDOM, FIRST }

    /** payloadType → set of node addresses that handle it. */
    private final Map<String, Set<NodeAddress>> handlers = new ConcurrentHashMap<>();

    private final ConnectionRegistry connectionRegistry;

    public ClusterRegistry(ConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    public void registerHandler(NodeAddress node, String payloadType) {
        handlers.computeIfAbsent(payloadType, k -> ConcurrentHashMap.newKeySet()).add(node);
    }

    public void registerHandlers(NodeAddress node, Collection<String> payloadTypes) {
        for (String type : payloadTypes) {
            registerHandler(node, type);
        }
        log.info("event=handlers_registered node={} count={}", node.instanceId(), payloadTypes.size());
    }

    /**
     * Remove a node from every payload binding. Called on node leave.
     */
    public void removeNode(NodeAddress node) {
        handlers.values().forEach(set -> set.remove(node));
        // Compact empty entries.
        handlers.entrySet().removeIf(e -> e.getValue().isEmpty());
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
     * Pick one available node for {@code payloadType}, using the supplied strategy.
     */
    public Optional<NodeAddress> pick(String payloadType, PickStrategy strategy) {
        Set<NodeAddress> candidates = availableNodesFor(payloadType);
        if (candidates.isEmpty()) return Optional.empty();
        return switch (strategy) {
            case RANDOM -> {
                int idx = ThreadLocalRandom.current().nextInt(candidates.size());
                int i = 0;
                NodeAddress chosen = null;
                for (NodeAddress addr : candidates) {
                    if (i++ == idx) { chosen = addr; break; }
                }
                yield Optional.ofNullable(chosen);
            }
            case FIRST -> candidates.stream().findFirst();
        };
    }

    public Optional<NodeAddress> pick(String payloadType) {
        return pick(payloadType, PickStrategy.RANDOM);
    }

    public Set<String> knownPayloadTypes() {
        return Collections.unmodifiableSet(Set.copyOf(handlers.keySet()));
    }

    public int handlerCount(String payloadType) {
        return handlers.getOrDefault(payloadType, Set.of()).size();
    }
}
