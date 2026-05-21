package com.evento.server.bus.v2.event;

import com.evento.server.bus.NodeAddress;
import com.evento.transport.protocol.BundleRegistrationInfo;

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
                BusEvent.BundleRegistered,
                BusEvent.NodeLeft,
                BusEvent.NodeEnabled,
                BusEvent.NodeDisabled,
                BusEvent.HeartbeatTimeout,
                BusEvent.ViewChanged,
                BusEvent.AvailableViewChanged,
                BusEvent.AdminNotification {

    Instant timestamp();

    /**
     * Fired when a bundle's transport handshake has completed and it has been
     * placed in the connection registry. Carries only the {@link NodeAddress}
     * — the bundle has not yet declared its handlers (that's
     * {@link BundleRegistered}).
     */
    record NodeJoined(NodeAddress node, Instant timestamp) implements BusEvent {}

    /**
     * Fired when a bundle sends its {@code evento:bundle-registration}
     * notification with handler + payload metadata. This is the v2 analogue of
     * v1 {@code MessageBus.addJoinListener(Consumer<BundleRegistration>)} and
     * is the event {@code AutoDiscoveryService} pattern-matches on to populate
     * components / handlers / payload schemas in the dashboard database.
     */
    record BundleRegistered(NodeAddress node, BundleRegistrationInfo registration, Instant timestamp) implements BusEvent {}

    /** A node disconnected. {@code reason} is best-effort context (may be null). */
    record NodeLeft(NodeAddress node, String reason, Instant timestamp) implements BusEvent {}

    record NodeEnabled(NodeAddress node, Instant timestamp) implements BusEvent {}

    record NodeDisabled(NodeAddress node, Instant timestamp) implements BusEvent {}

    record HeartbeatTimeout(NodeAddress node, long silentForMs, Instant timestamp) implements BusEvent {}

    record ViewChanged(java.util.Set<NodeAddress> view, Instant timestamp) implements BusEvent {}

    record AvailableViewChanged(java.util.Set<NodeAddress> availableView, Instant timestamp) implements BusEvent {}

    /**
     * Catch-all for application-level notifications from a bundle that aren't
     * one of the framework-reserved types ({@code evento:enable},
     * {@code evento:disable}, {@code evento:bundle-registration}). Server-side
     * subscribers pattern-match on this to handle their own protocol
     * extensions — e.g. the bundle-admin notification stream that carries
     * performance metrics and consumer-registration messages.
     */
    record AdminNotification(NodeAddress source, String payloadType, byte[] payload, Instant timestamp) implements BusEvent {}
}
