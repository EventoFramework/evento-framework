package com.evento.server.bus.v1adapter;

import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.server.bus.BusFacade;
import com.evento.server.bus.MessageBus;
import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.v2.event.BusEvent;
import com.evento.transport.protocol.BundleRegistrationInfo;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Adapter that lets the v1 {@link MessageBus} satisfy the {@link BusFacade}
 * contract used by the migrated controllers. Each {@link #subscribe} call
 * registers four shims against v1's four parallel listener lists and
 * translates each callback into the appropriate {@link BusEvent} subtype,
 * delivering everything through one stream.
 *
 * <p>The translation preserves semantics so the controllers behave identically
 * regardless of which bus is active. {@link BundleRegistration} → enriched
 * {@link BundleRegistrationInfo} keeps the rich {@link RegisteredHandler} list
 * and payload-schema map intact so {@code AutoDiscoveryService} can populate
 * the dashboard DB the same way it always has.
 *
 * <p>The two parallel view-changed lists become a single subscription that
 * fires both {@link BusEvent.ViewChanged} and
 * {@link BusEvent.AvailableViewChanged} as appropriate. Note: v1 fires those
 * callbacks itself; we just forward them.
 */
public final class MessageBusFacade implements BusFacade {

    private final MessageBus messageBus;
    /** Track subscribers so {@code MessageBus} listener-add doesn't grow unbounded across re-subscribes. */
    private final Set<Consumer<BusEvent>> subscribers = new CopyOnWriteArraySet<>();

    public MessageBusFacade(MessageBus messageBus) {
        this.messageBus = messageBus;
    }

    @Override
    public Set<NodeAddress> currentView() {
        return messageBus.getCurrentView();
    }

    @Override
    public Set<NodeAddress> currentAvailableView() {
        return messageBus.getCurrentAvailableView();
    }

    @Override
    public boolean isBundleAvailable(String bundleId) {
        return messageBus.isBundleAvailable(bundleId);
    }

    @Override
    public void subscribe(Consumer<BusEvent> listener) {
        subscribers.add(listener);
        messageBus.addJoinListener(reg -> listener.accept(translateJoin(reg)));
        messageBus.addLeaveListener(addr ->
                listener.accept(new BusEvent.NodeLeft(addr, "disconnected", Instant.now())));
        messageBus.addViewListener(view ->
                listener.accept(new BusEvent.ViewChanged(view, Instant.now())));
        messageBus.addAvailableViewListener(av ->
                listener.accept(new BusEvent.AvailableViewChanged(av, Instant.now())));
    }

    @Override
    public void forward(NodeAddress destination, EventoRequest request, Consumer<EventoResponse> callback) {
        messageBus.forward(null, destination, request, callback);
    }

    private static BusEvent.BundleRegistered translateJoin(BundleRegistration reg) {
        var node = new NodeAddress(reg.getBundleId(), reg.getBundleVersion(), reg.getInstanceId());
        var handlers = reg.getHandlers() == null ? List.<RegisteredHandler>of() : List.copyOf(reg.getHandlers());
        var payloadTypes = handlers.stream()
                .map(RegisteredHandler::getHandledPayload)
                .distinct()
                .collect(Collectors.toList());
        var payloadInfo = reg.getPayloadInfo() == null ? new HashMap<String, String[]>()
                : new HashMap<>(reg.getPayloadInfo());
        var info = new BundleRegistrationInfo(reg.getBundleVersion(), payloadTypes, handlers, payloadInfo);
        return new BusEvent.BundleRegistered(node, info, Instant.now());
    }
}
