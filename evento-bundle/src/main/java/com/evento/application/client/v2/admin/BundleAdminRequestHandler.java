package com.evento.application.client.v2.admin;

import com.evento.application.client.v2.handler.HandlerRegistry;
import com.evento.application.consumer.EventConsumer;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerDeleteDeadEventRequestMessage;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerFetchStatusRequestMessage;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerProcessDeadQueueRequestMessage;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerResponseMessage;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerSetEventRetryRequestMessage;

import java.util.Optional;

/**
 * Bundle-side counterpart of {@code BusLifecycleFacade.forward(...)} on the
 * server: a {@link HandlerRegistry.RequestHandler} for the
 * {@code evento:server-admin-request} payloadType.
 *
 * <p>Decodes the inbound CBOR bytes into an {@link EventoRequest} via
 * {@link AdminPayloadCodec}, dispatches the inner body to the matching
 * {@link EventConsumer} operation, then re-encodes the {@link EventoResponse}
 * to return as the response payload.
 *
 * <p>Closes the round-trip the dashboard / discovery / consumer endpoints
 * rely on: {@code ConsumerService.getConsumerStatusFromNodes},
 * {@code setRetryForConsumerEvent}, {@code consumeDeadQueue},
 * {@code deleteDeadEventFromEventConsumer}.
 *
 * <p>Construction takes a {@link ConsumerLookup} so callers control how
 * consumers are resolved — the existing {@code EventoBundle.getEventConsumer}
 * is the production implementation; tests use a function literal.
 */
public final class BundleAdminRequestHandler implements HandlerRegistry.RequestHandler {

    /** SPI for resolving an {@link EventConsumer} by id + component type. */
    @FunctionalInterface
    public interface ConsumerLookup {
        Optional<? extends EventConsumer> find(String consumerId, ComponentType componentType);
    }

    private final AdminPayloadCodec codec;
    private final ConsumerLookup consumerLookup;

    public BundleAdminRequestHandler(AdminPayloadCodec codec, ConsumerLookup consumerLookup) {
        this.codec = codec;
        this.consumerLookup = consumerLookup;
    }

    @Override
    public byte[] handle(byte[] payload, HandlerRegistry.RequestContext context) {
        var request = codec.decodeRequest(payload);
        var reply = new EventoResponse();
        reply.setCorrelationId(request.getCorrelationId());
        reply.setTimestamp(System.currentTimeMillis());
        try {
            reply.setBody(dispatch(request.getBody()));
        } catch (Throwable t) {
            reply.setBody(new ExceptionWrapper(t));
        }
        return codec.encodeResponse(reply);
    }

    private java.io.Serializable dispatch(Object body) throws Exception {
        return switch (body) {
            case ConsumerFetchStatusRequestMessage m ->
                    consumerLookup.find(m.getConsumerId(), m.getComponentType())
                            .map(EventConsumer::toConsumerStatus)
                            .orElseThrow(() -> notFound(m.getConsumerId(), m.getComponentType()));
            case ConsumerSetEventRetryRequestMessage m -> {
                var consumer = consumerLookup.find(m.getConsumerId(), m.getComponentType())
                        .orElseThrow(() -> notFound(m.getConsumerId(), m.getComponentType()));
                consumer.setDeadEventRetry(m.getEventSequenceNumber(), m.isRetry());
                yield ok();
            }
            case ConsumerProcessDeadQueueRequestMessage m -> {
                var consumer = consumerLookup.find(m.getConsumerId(), m.getComponentType())
                        .orElseThrow(() -> notFound(m.getConsumerId(), m.getComponentType()));
                consumer.consumeDeadEventQueue();
                yield ok();
            }
            case ConsumerDeleteDeadEventRequestMessage m -> {
                var consumer = consumerLookup.find(m.getConsumerId(), m.getComponentType())
                        .orElseThrow(() -> notFound(m.getConsumerId(), m.getComponentType()));
                consumer.deleteDeadEvent(m.getEventSequenceNumber());
                yield ok();
            }
            case null -> throw new IllegalArgumentException("admin request body is null");
            default -> throw new IllegalArgumentException(
                    "unsupported admin request body type: " + body.getClass().getName());
        };
    }

    private static ConsumerResponseMessage ok() {
        var resp = new ConsumerResponseMessage();
        resp.setSuccess(true);
        return resp;
    }

    private static IllegalStateException notFound(String consumerId, ComponentType type) {
        return new IllegalStateException("consumer not found: " + type + "/" + consumerId);
    }
}
