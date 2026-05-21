package com.evento.application.client.v2;

import com.evento.application.client.v2.handler.HandlerRegistry;
import com.evento.application.manager.AggregateManager;
import com.evento.application.manager.ProjectionManager;
import com.evento.application.manager.ServiceManager;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.application.DecoratedDomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.QueryMessage;
import com.evento.common.modeling.messaging.message.application.ServiceCommandMessage;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;

import java.io.Serializable;

/**
 * Ports the v1 switch-on-body-type dispatch lambda
 * (see {@code EventoBundle.Builder.start()}, lines 589-642 pre-v2) into the
 * v2 {@link HandlerRegistry.RequestHandler} shape: byte[] in, byte[] out.
 *
 * <p>Decodes the inbound CBOR payload to a v1 {@code EventoRequest}, demuxes
 * by the wrapped body type to the right manager, wraps the manager's return
 * value in a v1 {@code EventoResponse}, and re-encodes for the wire. Failures
 * become {@code ExceptionWrapper} bodies — same shape v1 used, so caller-side
 * decode in {@link EventoServerV2Adapter#request} continues to work.
 *
 * <p>One dispatcher instance is registered against every command / query
 * payloadType the bundle declares — the routing decision was already made by
 * the v2 broker (it picked the bundle by payloadType), this class just runs
 * the right manager for the wrapper type.
 */
public final class BundleInboundDispatcher implements HandlerRegistry.RequestHandler {

    private final AdminPayloadCodec codec;
    private final AggregateManager aggregateManager;
    private final ServiceManager serviceManager;
    private final ProjectionManager projectionManager;

    public BundleInboundDispatcher(AdminPayloadCodec codec,
                                   AggregateManager aggregateManager,
                                   ServiceManager serviceManager,
                                   ProjectionManager projectionManager) {
        this.codec = codec;
        this.aggregateManager = aggregateManager;
        this.serviceManager = serviceManager;
        this.projectionManager = projectionManager;
    }

    @Override
    public byte[] handle(byte[] payload, HandlerRegistry.RequestContext context) {
        var request = codec.decodeRequest(payload);
        var reply = new EventoResponse();
        reply.setCorrelationId(request.getCorrelationId());
        reply.setRequestTimestamp(request.getTimestamp());
        reply.setTimeout(request.getTimeout());
        reply.setUnit(request.getUnit());
        try {
            reply.setBody(dispatch(request.getBody()));
        } catch (Throwable t) {
            reply.setBody(new ExceptionWrapper(t));
        }
        return codec.encodeResponse(reply);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Serializable dispatch(Object body) throws Throwable {
        return switch (body) {
            case DecoratedDomainCommandMessage cm -> aggregateManager.handle(cm);
            case ServiceCommandMessage sm -> serviceManager.handle(sm);
            case QueryMessage qm -> projectionManager.handle(qm);
            case null -> throw new IllegalArgumentException("inbound request body is null");
            default -> throw new IllegalArgumentException(
                    "unsupported inbound request body type: " + body.getClass().getName());
        };
    }
}
