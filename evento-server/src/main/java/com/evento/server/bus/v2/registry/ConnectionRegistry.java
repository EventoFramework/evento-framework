package com.evento.server.bus.v2.registry;

import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.v2.event.BusEvent;
import com.evento.server.bus.v2.event.BusEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Atomic registry of bundle connections. Replaces the {@code view},
 * {@code registrations}, {@code availableView}, and {@code connectionIds} maps
 * from v1 {@code MessageBus}, plus the matching {@code synchronized} blocks,
 * with a single source of truth + a derived available-set.
 *
 * <p>Concurrency model: a {@link ConcurrentHashMap} holds the full view; a
 * second {@link ConcurrentHashMap} (used as a set) holds the enabled subset.
 * All read paths return snapshots ({@code Set.copyOf}) so listeners can iterate
 * freely without holding a lock. All write paths are lock-free CAS via
 * {@code compute*} primitives.
 *
 * <p>The "supersede" semantics — when a bundle re-registers with the same
 * {@code instanceId}, the old {@link Connection} is closed and replaced — fix
 * a v1 race where a stale {@code leave} from the old socket could remove the
 * just-registered new one.
 */
public final class ConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRegistry.class);

    private final Map<NodeAddress, Connection> view = new ConcurrentHashMap<>();
    private final Map<NodeAddress, Boolean> enabledView = new ConcurrentHashMap<>();
    private final BusEventBus eventBus;

    public ConnectionRegistry(BusEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Register a fresh connection. If a connection for the same {@link NodeAddress}
     * already exists, it is closed and replaced; the resulting events are still
     * {@code NodeJoined} (the cluster perspective is unchanged), but a
     * {@code NodeLeft("superseded")} is published first.
     */
    public Connection register(Connection conn) {
        var previous = view.put(conn.address(), conn);
        if (previous != null) {
            log.warn("event=connection_superseded node={} old_token={} new_token={}",
                    conn.address().instanceId(), previous.connectionToken(), conn.connectionToken());
            try {
                previous.transport().close();
            } catch (Throwable t) {
                log.warn("event=supersede_close_failed node={}", conn.address().instanceId(), t);
            }
            eventBus.publish(new BusEvent.NodeLeft(previous.address(), "superseded", Instant.now()));
        }
        eventBus.publish(new BusEvent.NodeJoined(conn.address(), Instant.now()));
        publishViewChanged();
        return previous;
    }

    /**
     * Remove a connection. The {@code expectedToken} guards against ghost-leaves:
     * if the current entry has a different token (because a newer connection took
     * its place), the unregister is rejected.
     *
     * @return the removed {@link Connection}, or empty if the address was not
     *         registered or the token mismatched.
     */
    public Optional<Connection> unregister(NodeAddress address, String expectedToken, String reason) {
        var holder = new Connection[1];
        view.compute(address, (k, existing) -> {
            if (existing == null) return null;
            if (expectedToken != null && !expectedToken.equals(existing.connectionToken())) {
                log.warn("event=unregister_token_mismatch node={} expected={} actual={}",
                        address.instanceId(), expectedToken, existing.connectionToken());
                return existing;
            }
            holder[0] = existing;
            return null;
        });
        if (holder[0] == null) {
            return Optional.empty();
        }
        enabledView.remove(address);
        eventBus.publish(new BusEvent.NodeLeft(address, reason, Instant.now()));
        publishViewChanged();
        publishAvailableViewChanged();
        return Optional.of(holder[0]);
    }

    public boolean enable(NodeAddress address) {
        if (!view.containsKey(address)) {
            log.warn("event=enable_unknown_node node={}", address.instanceId());
            return false;
        }
        if (enabledView.put(address, Boolean.TRUE) == null) {
            eventBus.publish(new BusEvent.NodeEnabled(address, Instant.now()));
            publishAvailableViewChanged();
            return true;
        }
        return false;
    }

    public boolean disable(NodeAddress address) {
        if (enabledView.remove(address) != null) {
            eventBus.publish(new BusEvent.NodeDisabled(address, Instant.now()));
            publishAvailableViewChanged();
            return true;
        }
        return false;
    }

    public Optional<Connection> lookup(NodeAddress address) {
        return Optional.ofNullable(view.get(address));
    }

    /**
     * Lock-free snapshot of all registered nodes.
     */
    public Set<NodeAddress> view() {
        return Collections.unmodifiableSet(Set.copyOf(view.keySet()));
    }

    /**
     * Lock-free snapshot of all enabled nodes.
     */
    public Set<NodeAddress> availableView() {
        return Collections.unmodifiableSet(Set.copyOf(enabledView.keySet()));
    }

    public int size() {
        return view.size();
    }

    public boolean isAvailable(NodeAddress address) {
        return enabledView.containsKey(address);
    }

    public boolean isAvailable(String bundleId) {
        for (NodeAddress addr : enabledView.keySet()) {
            if (addr.bundleId().equals(bundleId)) return true;
        }
        return false;
    }

    /**
     * Close all connections and clear the registry. Used by lifecycle shutdown.
     */
    public void closeAll(String reason) {
        for (var entry : view.entrySet()) {
            try {
                entry.getValue().transport().close();
            } catch (Throwable t) {
                log.warn("event=close_failed node={}", entry.getKey().instanceId(), t);
            }
        }
        view.clear();
        enabledView.clear();
        log.info("event=registry_cleared reason={}", reason);
    }

    private void publishViewChanged() {
        eventBus.publish(new BusEvent.ViewChanged(view(), Instant.now()));
    }

    private void publishAvailableViewChanged() {
        eventBus.publish(new BusEvent.AvailableViewChanged(availableView(), Instant.now()));
    }
}
