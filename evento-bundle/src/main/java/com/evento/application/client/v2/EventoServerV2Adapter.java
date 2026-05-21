package com.evento.application.client.v2;

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
public final class EventoServerV2Adapter implements EventoServer {

    private final BundleClient client;
    private final AdminPayloadCodec codec;
    private final String bundleId;
    private final String instanceId;
    private final long bundleVersion;

    public EventoServerV2Adapter(BundleClient client,
                                 String bundleId,
                                 String instanceId,
                                 long bundleVersion) {
        this(client, bundleId, instanceId, bundleVersion, new AdminPayloadCodec());
    }

    public EventoServerV2Adapter(BundleClient client,
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
            return client.request(payloadType, codec.encodeRequest(envelope), timeoutDuration)
                    .thenApply(response -> {
                        if (response.isError()) {
                            throw new CompletionException(new IllegalStateException(
                                    response.error() == null ? "request failed" : response.error().message()));
                        }
                        var decoded = codec.decodeResponse(response.payload());
                        var body = decoded.getBody();
                        if (body instanceof ExceptionWrapper wrap) {
                            throw new CompletionException(wrap.toException());
                        }
                        return (T) body;
                    });
        } catch (Exception e) {
            throw new SendFailedException(e);
        }
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
    private String resolvePayloadType(Serializable request) {
        if (request instanceof CommandMessage<?> cm) return cm.getCommandName();
        if (request instanceof QueryMessage<?> qm) return qm.getQueryName();
        if (request instanceof Message<?> m) return m.getPayloadName();
        String simple = request.getClass().getSimpleName();
        return simple.isEmpty() ? request.getClass().getName() : simple;
    }
}
