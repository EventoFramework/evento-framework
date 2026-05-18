package com.evento.server.bus.v2.event;

import com.evento.server.bus.NodeAddress;

import java.time.Instant;

/**
 * Sealed type for cluster lifecycle events. Replaces the four parallel listener
 * lists ({@code joinListeners}, {@code leaveListeners}, {@code viewListeners},
 * {@code availableViewListeners}) of v1 {@code MessageBus} with a single
 * {@code Consumer<BusEvent>} surface — callers pattern-match on the cases they
 * care about and ignore the rest. New event types can be added by extending the
 * sealed hierarchy; existing subscribers compile-time fail to acknowledge them
 * via exhaustive switches, which is the explicit OCP-friendly handoff.
 */
public sealed interface BusEvent
        permits BusEvent.NodeJoined,
                BusEvent.NodeLeft,
                BusEvent.NodeEnabled,
                BusEvent.NodeDisabled,
                BusEvent.HeartbeatTimeout,
                BusEvent.ViewChanged,
                BusEvent.AvailableViewChanged {

    Instant timestamp();

    record NodeJoined(NodeAddress node, Instant timestamp) implements BusEvent {}

    /** A node disconnected. {@code reason} is best-effort context (may be null). */
    record NodeLeft(NodeAddress node, String reason, Instant timestamp) implements BusEvent {}

    record NodeEnabled(NodeAddress node, Instant timestamp) implements BusEvent {}

    record NodeDisabled(NodeAddress node, Instant timestamp) implements BusEvent {}

    record HeartbeatTimeout(NodeAddress node, long silentForMs, Instant timestamp) implements BusEvent {}

    record ViewChanged(java.util.Set<NodeAddress> view, Instant timestamp) implements BusEvent {}

    record AvailableViewChanged(java.util.Set<NodeAddress> availableView, Instant timestamp) implements BusEvent {}
}
