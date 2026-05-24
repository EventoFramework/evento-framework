package com.evento.server.bus;

import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.server.bus.BusFacade;
import com.evento.server.bus.NodeAddress;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.server.bus.event.BusEvent;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.transport.message.Response;
import com.evento.transport.protocol.ProtocolPayloadTypes;

import java.time.Duration;
import java.util.Set;
import java.util.function.Consumer;

/**
 * v2 implementation of {@link BusFacade}: thin adapter over {@link BusLifecycle}.
 *
 * <p>The view / availability / kill / subscribe surface delegates directly. The
 * {@link #forward} method bridges between v1's rich {@link EventoRequest} (what
 * dashboard controllers manipulate) and v2's opaque {@code byte[]} wire by
 * CBOR-encoding the request under
 * {@link ProtocolPayloadTypes#SERVER_ADMIN_REQUEST}, awaiting the matching
 * {@link Response}, and decoding the response back into an
 * {@link EventoResponse}.
 *
 * <p>On the receiving end, a v2 bundle must register a handler for
 * {@code evento:server-admin-request} that uses {@link AdminPayloadCodec} to
 * decode the request and re-encode the response. That handler is owned by the
 * bundle migration in PR3.2 — for the IT in this slice the test bundle wires
 * it manually.
 */
public final class BusLifecycleFacade implements BusFacade {

    private final BusLifecycle lifecycle;
    private final AdminPayloadCodec adminCodec;
    private final Duration defaultForwardTimeout;

    public BusLifecycleFacade(BusLifecycle lifecycle) {
        this(lifecycle, new AdminPayloadCodec(), Duration.ofSeconds(30));
    }

    public BusLifecycleFacade(BusLifecycle lifecycle,
                              AdminPayloadCodec adminCodec,
                              Duration defaultForwardTimeout) {
        this.lifecycle = lifecycle;
        this.adminCodec = adminCodec;
        this.defaultForwardTimeout = defaultForwardTimeout;
    }

    @Override
    public Set<NodeAddress> currentView() {
        return lifecycle.view();
    }

    @Override
    public Set<NodeAddress> currentAvailableView() {
        return lifecycle.availableView();
    }

    @Override
    public boolean isBundleAvailable(String bundleId) {
        return lifecycle.isBundleAvailable(bundleId);
    }

    @Override
    public void subscribe(Consumer<BusEvent> listener) {
        lifecycle.subscribe(listener);
    }

    @Override
    public void forward(NodeAddress destination, EventoRequest request, Consumer<EventoResponse> callback) {
        byte[] encoded = adminCodec.encodeRequest(request);
        long timeoutMs = request.getUnit() == null ? defaultForwardTimeout.toMillis()
                : request.getUnit().toMillis(request.getTimeout());
        Duration timeout = timeoutMs > 0 ? Duration.ofMillis(timeoutMs) : defaultForwardTimeout;
        lifecycle.forward(destination, ProtocolPayloadTypes.SERVER_ADMIN_REQUEST, encoded, timeout)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        callback.accept(buildErrorResponse(request.getCorrelationId(), error));
                        return;
                    }
                    if (response.isError()) {
                        callback.accept(buildErrorResponse(request.getCorrelationId(),
                                new IllegalStateException(response.error().message())));
                        return;
                    }
                    try {
                        callback.accept(adminCodec.decodeResponse(response.payload()));
                    } catch (RuntimeException decodeError) {
                        callback.accept(buildErrorResponse(request.getCorrelationId(), decodeError));
                    }
                });
    }

    private static EventoResponse buildErrorResponse(String correlationId, Throwable cause) {
        var resp = new EventoResponse();
        resp.setCorrelationId(correlationId);
        resp.setBody(new ExceptionWrapper(cause));
        resp.setTimestamp(System.currentTimeMillis());
        return resp;
    }
}
