package com.evento.server.bus;

import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.server.bus.v2.event.BusEvent;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Stable contract that the dashboard, discovery and consumer services depend on
 * — implemented by both the legacy v1 {@code MessageBus} and the new v2
 * {@code BusLifecycle} (via thin adapters). Lets {@code evento.server.bus.v2.enabled}
 * flip the implementation under the controllers without code changes.
 *
 * <p>The surface is intentionally smaller than v1's {@code MessageBus}: the four
 * parallel listener lists (view / availableView / join / leave) collapse into a
 * single {@link #subscribe(Consumer)} taking a sealed {@link BusEvent} stream;
 * callers pattern-match the cases they care about. {@code addXListener} /
 * {@code removeXListener} are gone — the use case for transient removal is
 * "this subscriber failed to send" which is now expressed by the subscriber
 * itself (e.g. SseEmitter wrappers can self-unregister via a returned token).
 *
 * <p>{@link #forward(NodeAddress, EventoRequest, Consumer)} is the
 * server-initiated RPC primitive: the dashboard ConsumerService uses it to ask
 * a specific bundle for its consumer status. On the v2 path the body is encoded
 * as CBOR under the {@code evento:server-admin-request} payloadType.
 */
public interface BusFacade {

    /** Snapshot of every connected bundle node. */
    Set<NodeAddress> currentView();

    /**
     * Snapshot restricted to nodes that have sent {@code evento:enable} and are
     * therefore eligible for routing.
     */
    Set<NodeAddress> currentAvailableView();

    /** True if any node in the available view belongs to {@code bundleId}. */
    boolean isBundleAvailable(String bundleId);

    /**
     * Subscribe to the cluster lifecycle event stream. Subscribers are notified
     * synchronously on the publisher thread; a slow subscriber slows the bus.
     * Subscribers register-once-and-stay — there is no unsubscribe API. Lambda
     * captures should hold weak references when retention matters.
     */
    void subscribe(Consumer<BusEvent> listener);

    /**
     * Server-initiated RPC to a specific destination bundle. The callback is
     * invoked exactly once with the {@link EventoResponse} when it arrives (or
     * on timeout / disconnect with an error-carrying response).
     */
    void forward(NodeAddress destination, EventoRequest request, Consumer<EventoResponse> callback);
}
