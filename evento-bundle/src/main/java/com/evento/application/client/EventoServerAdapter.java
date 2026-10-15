package com.evento.application.client;

import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.bus.SendFailedException;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.application.CommandMessage;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.QueryMessage;
import com.evento.common.modeling.messaging.message.internal.EventoMessage;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.transport.protocol.ProtocolPayloadTypes;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Implements the v1 {@link EventoServer} contract on top of the v2
 * {@link BundleClient}. Lets the existing {@code CommandGatewayImpl} /
 * {@code QueryGatewayImpl} keep working unchanged on the v2 wire.
 *
 * <p>The two methods translate as follows:
 *
 * <ul>
 *   <li>{@link #request(Serializable, long, TimeUnit)} — wraps the body in an
 *       {@link EventoRequest}, CBOR-encodes it via {@link AdminPayloadCodec},
 *       and sends as a v2 {@code Request} with {@code payloadType} =
 *       {@link Message#getPayloadName()} so the server routes by the
 *       command / query simple class name (the same convention v1 used in
 *       {@code RegisteredHandler.handledPayload}). On reply, decodes the
 *       {@code EventoResponse} and completes the future with its body.</li>
 *   <li>{@link #send(Serializable)} — wraps the body in an
 *       {@link EventoMessage}, CBOR-encodes it, and emits a v2
 *       {@code Notification} under
 *       {@link ProtocolPayloadTypes#BUNDLE_ADMIN_NOTIFICATION}. The server-side
 *       {@code BundleAdminNotificationListener} decodes and dispatches.</li>
 * </ul>
 */
public final class EventoServerAdapter implements EventoServer {

    private final BundleClient client;
    private final AdminPayloadCodec codec;
    private final String bundleId;
    private final String instanceId;
    private final long bundleVersion;
    /** Every write this process's commands made: context → highest stored sequence number. */
    private final java.util.concurrent.atomic.AtomicReference<com.evento.common.modeling.messaging.query.ConsistencyToken>
            ownWrites = new java.util.concurrent.atomic.AtomicReference<>(
                    com.evento.common.modeling.messaging.query.ConsistencyToken.NONE);

    public EventoServerAdapter(BundleClient client,
                                 String bundleId,
                                 String instanceId,
                                 long bundleVersion) {
        this(client, bundleId, instanceId, bundleVersion, new AdminPayloadCodec());
    }

    public EventoServerAdapter(BundleClient client,
                                 String bundleId,
                                 String instanceId,
                                 long bundleVersion,
                                 AdminPayloadCodec codec) {
        this.client = client;
        this.codec = codec;
        this.bundleId = bundleId;
        this.instanceId = instanceId;
        this.bundleVersion = bundleVersion;
    }

    @Override
    public void send(Serializable message) throws SendFailedException {
        var envelope = new EventoMessage();
        envelope.setSourceBundleId(bundleId);
        envelope.setSourceInstanceId(instanceId);
        envelope.setSourceBundleVersion(bundleVersion);
        envelope.setBody(message);
        try {
            client.notify(ProtocolPayloadTypes.BUNDLE_ADMIN_NOTIFICATION,
                    codec.encodeMessage(envelope));
        } catch (Exception e) {
            throw new SendFailedException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Serializable> CompletableFuture<T> request(Serializable request,
                                                                  long timeout,
                                                                  TimeUnit unit) throws SendFailedException {
        var envelope = new EventoRequest();
        envelope.setSourceBundleId(bundleId);
        envelope.setSourceInstanceId(instanceId);
        envelope.setSourceBundleVersion(bundleVersion);
        envelope.setCorrelationId(UUID.randomUUID().toString());
        envelope.setBody(request);
        envelope.setTimestamp(Instant.now().toEpochMilli());
        envelope.setTimeout(timeout);
        envelope.setUnit(unit);

        String payloadType = resolvePayloadType(request);
        Duration timeoutDuration = Duration.ofMillis(unit.toMillis(timeout));
        try {
            return client.request(payloadType, codec.encodeRequest(envelope), timeoutDuration, isIdempotentRead(request))
                    .thenApply(response -> {
                        if (response.isError()) {
                            throw new CompletionException(toException(response.error(), payloadType));
                        }
                        var decoded = codec.decodeResponse(response.payload());
                        var body = decoded.getBody();
                        if (body instanceof ExceptionWrapper wrap) {
                            throw new CompletionException(wrap.toException());
                        }
                        recordWrite(decoded, body);
                        return (T) body;
                    });
        } catch (Exception e) {
            throw new SendFailedException(e);
        }
    }

    /**
     * Rebuild a typed exception from the wire error.
     *
     * <p>A transport failure carries an {@code exceptionClassName}, and for
     * timeouts that distinction is the whole point: a timeout means the caller
     * stopped waiting, not that the handler rejected anything, so the work may
     * well have been applied. Flattening both into one generic exception forces
     * every caller to report an indeterminate outcome as a definite failure —
     * which is how a slow-but-successful command ends up surfaced to a user as
     * an error, and how a blind retry applies it a second time. A retry is safe
     * only when the command carries an idempotency key
     * ({@code CommandGateway.sendIdempotent}, or the key a consumer handler's
     * gateway derives): the broker then answers it with the event the first
     * attempt stored, on whichever server the retry lands (G5).
     */
    static RuntimeException toException(com.evento.transport.message.ResponseError error,
                                                String payloadType) {
        if (error == null) {
            return new IllegalStateException("request failed: " + payloadType);
        }
        String message = error.message() == null ? "request failed" : error.message();
        // Checked before its parent type: a caller that cares can tell "the broker
        // changed under me" from a plain timeout, one that does not still catches
        // it as a RequestTimeoutException.
        if (com.evento.transport.BrokerChangedException.class.getName().equals(error.exceptionClassName())) {
            return new com.evento.transport.BrokerChangedException(payloadType + ": " + message);
        }
        if (com.evento.transport.RequestTimeoutException.class.getName().equals(error.exceptionClassName())) {
            return new com.evento.transport.RequestTimeoutException(payloadType + ": " + message);
        }
        // Determinate, unlike a timeout: the broker refused it before executing anything.
        if (com.evento.transport.RequestExpiredException.class.getName().equals(error.exceptionClassName())) {
            return new com.evento.transport.RequestExpiredException(payloadType + ": " + message);
        }
        if (com.evento.transport.TooManyPendingRequestsException.class.getName()
                .equals(error.exceptionClassName())) {
            return new com.evento.transport.TooManyPendingRequestsException(payloadType + ": " + message);
        }
        // Refused before anything was written (between sessions, or a fail-back drain):
        // definitely not executed, so safe to retry. Still an IllegalStateException, as
        // it surfaced before, for code that catches that.
        if (com.evento.transport.SendFailedException.class.getName().equals(error.exceptionClassName())) {
            return new com.evento.transport.RequestNotSentException(payloadType + ": " + message);
        }
        return new IllegalStateException(message);
    }

    /**
     * A command's reply says where its event was stored (a 2.6 broker): remember it for
     * {@link #ownWrites()} and tag the returned event with it ({@code sendTracked}).
     */
    private void recordWrite(com.evento.common.modeling.messaging.message.internal.EventoResponse decoded,
                             Serializable body) {
        var seq = decoded.getEventSequenceNumber();
        if (seq == null || seq < 0) return;
        var token = com.evento.common.modeling.messaging.query.ConsistencyToken.of(decoded.getEventContext(), seq);
        ownWrites.accumulateAndGet(token, com.evento.common.modeling.messaging.query.ConsistencyToken::merge);
        if (body instanceof com.evento.common.modeling.messaging.message.application.EventMessage<?> em) {
            em.setStoredAt(token);
        }
    }

    /**
     * Streams the answer of {@code query} (2.6): views pulled as they are consumed, the
     * frames carried by the broker with flow control, or answered in process when this
     * bundle handles the query itself. Returns a stream in every case: when the session or
     * the answering bundle cannot stream, the whole answer arrives in one reply and is
     * iterated the same way.
     */
    @Override
    public <V extends com.evento.common.modeling.messaging.payload.View>
    com.evento.common.messaging.gateway.QueryStream<V> streamQuery(QueryMessage<?> query,
                                                                   com.evento.common.messaging.gateway.QueryOptions options,
                                                                   long timeoutMillis) {
        var envelope = new EventoRequest();
        envelope.setSourceBundleId(bundleId);
        envelope.setSourceInstanceId(instanceId);
        envelope.setSourceBundleVersion(bundleVersion);
        envelope.setCorrelationId(UUID.randomUUID().toString());
        envelope.setBody(query);
        envelope.setTimestamp(Instant.now().toEpochMilli());
        envelope.setTimeout(timeoutMillis);
        envelope.setUnit(TimeUnit.MILLISECONDS);
        var frames = client.openStream(query.getQueryName(), codec.encodeRequest(envelope),
                Duration.ofMillis(timeoutMillis), options.getWindow(), options.isDirect());
        return new com.evento.application.client.stream.FrameQueryStream<>(frames, codec,
                EventoServerAdapter::toException);
    }

    @Override
    public com.evento.common.modeling.messaging.query.ConsistencyToken ownWrites() {
        return ownWrites.get();
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public String getBundleId() {
        return bundleId;
    }

    /**
     * Sends the {@code evento:enable} notification so the server marks this
     * bundle as available for routing. Equivalent to v1
     * {@code EventoServerClient.enable()}.
     */
    public void enable() {
        client.enable().join();
    }

    /**
     * Sends the {@code evento:disable} notification so the server takes this
     * bundle out of the available view.
     */
    public void disable() {
        client.disable().join();
    }

    /** Closes the underlying transport. */
    public void close() {
        client.close();
    }

    /** Exposes the wrapped client for callers that need richer control. */
    public BundleClient client() {
        return client;
    }

    /**
     * Pick the payloadType the v2 server routes on.
     *
     * <p>For wrapped command / query messages we use the simple name of the
     * inner payload — that's the convention v1 declared in
     * {@code RegisteredHandler.handledPayload} and what
     * {@code AutoDiscoveryService} persists. For other request bodies
     * (rare — mostly framework-internal) we fall back to the body's simple
     * class name.
     */
    /**
     * Reads that may run any number of times: queries and the consumers' event-store
     * reads. They are asked again when a session ends with them in flight (see
     * {@link BundleClient#request(String, byte[], Duration, boolean)}).
     */
    static boolean isIdempotentRead(Serializable request) {
        return request instanceof QueryMessage<?>
                || request instanceof com.evento.common.messaging.consumer.EventFetchRequest
                || request instanceof com.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
    }

    private String resolvePayloadType(Serializable request) {
        if (request instanceof CommandMessage<?> cm) return cm.getCommandName();
        if (request instanceof QueryMessage<?> qm) return qm.getQueryName();
        if (request instanceof Message<?> m) return m.getPayloadName();
        String simple = request.getClass().getSimpleName();
        return simple.isEmpty() ? request.getClass().getName() : simple;
    }
}
