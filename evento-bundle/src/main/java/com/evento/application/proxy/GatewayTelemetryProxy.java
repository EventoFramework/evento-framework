package com.evento.application.proxy;

import com.evento.application.performance.TracingAgent;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.messaging.message.application.CommandMessage;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.messaging.payload.Payload;
import com.evento.common.modeling.messaging.payload.TrackablePayload;
import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.QueryResponse;
import com.evento.common.performance.PerformanceService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The GatewayTelemetryProxy class acts as a proxy for CommandGateway and QueryGateway,
 * adding telemetry and performance tracking functionality.
 *
 * <p><b>Derived idempotency keys (G5).</b> One proxy is created per handled message. When a
 * consumer engine (saga, projector, observer) scopes it with
 * {@link #withDerivedIdempotencyKeys(String, long)}, every command the handler sends without an
 * explicit key gets {@code evento:<consumerId>:<eventSequenceNumber>:<ordinal>}, the ordinal
 * counting the commands sent in this handler invocation from 0. A redelivered event (the consumer
 * failed or crashed before its checkpoint, a dead event retried, the handler's own retry loop)
 * therefore re-issues the same keys, and the broker answers the commands that already ran with
 * their stored events instead of running them again. The ordinal is reset by
 * {@link #beginAttempt()} before each attempt of the handler's retry loop. Deterministic as long
 * as the handler sends its commands in a deterministic order; a handler that fans commands out
 * from several threads gets ordinals in arrival order and should pass explicit keys instead.
 */
public class GatewayTelemetryProxy implements CommandGateway, QueryGateway {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    private final String bundle;
    private final String instanceId;
    private final String componentName;

    private final PerformanceService performanceService;

    private final HashMap<String, AtomicInteger> invocationCounter = new HashMap<>();

    private final Message<?> handledMessage;
    private final TracingAgent tracingAgent;

    private boolean forceTelemetry = false;

    /** {@code consumerId:eventSequenceNumber} when commands get derived keys, else {@code null}. */
    private volatile String idempotencyScope;
    private final AtomicInteger commandOrdinal = new AtomicInteger();

    /** Prefix of every derived key, so it cannot be mistaken for a caller-chosen one. */
    static final String DERIVED_KEY_PREFIX = "evento:";

    /**
     * Constructs a new GatewayTelemetryProxy with the specified parameters.
     *
     * @param commandGateway     The command gateway to proxy.
     * @param queryGateway       The query gateway to proxy.
     * @param bundle             The bundle identifier.
     * @param performanceService The performance service for tracking metrics.
     * @param componentName      The name of the component associated with the proxy.
     * @param handledMessage     The message being handled by the proxy.
     * @param tracingAgent       The tracing agent for correlating and tracking.
     * @param instanceId         The cluster node identifier
     */
    public GatewayTelemetryProxy(CommandGateway commandGateway,
                                 QueryGateway queryGateway,
                                 String bundle, PerformanceService performanceService,
                                 String componentName,
                                 Message<?> handledMessage,
                                 TracingAgent tracingAgent,
                                 String instanceId) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
        this.bundle = bundle;
        this.performanceService = performanceService;
        this.componentName = componentName;
        this.handledMessage = handledMessage;
        this.tracingAgent = tracingAgent;
        this.instanceId = instanceId;
    }


    @Override
    public <R> CompletableFuture<R> send(Command command) {
       return send(command, null, handledMessage);
    }


    @Override
    public <R> CompletableFuture<R> send(Command command, long timeout, TimeUnit unit) {
       return send(command, null, handledMessage, timeout, unit);
    }


    @Override
    public <R> CompletableFuture<R> send(Command command, Metadata metadata) {
       return send(command, metadata, handledMessage);
    }

    @Override
    public <R> CompletableFuture<R> send(Command command, Metadata metadata, long timeout, TimeUnit unit) {
       return send(command, metadata, handledMessage, timeout, unit);
    }


    @Override
    public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage) {
       return send(command, metadata, handledMessage, getDefaultTimeoutTime(), getDefaultTimeoutUnit());
    }

    @Override
    public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage, long timeout, TimeUnit unit) {
        return send(command, metadata, handledMessage, timeout, unit, null);
    }

    /**
     * Sends with the explicit key when given, else with the derived key when this proxy is
     * scoped to a consumed event, else without a key. Every command takes an ordinal, keyed or
     * not, so an explicit key on one command does not shift the derived keys of the others.
     */
    @Override
    public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                         long timeout, TimeUnit unit, String idempotencyKey) {
        updateInvocationCounter(command);
        int ordinal = commandOrdinal.getAndIncrement();
        var key = CommandMessage.normalizeIdempotencyKey(idempotencyKey);
        var scope = idempotencyScope;
        if (key == null && scope != null) {
            key = deriveIdempotencyKey(scope, ordinal);
        }
        return commandGateway.send(command, tracingAgent.correlate(metadata, this.handledMessage),
                this.handledMessage, timeout, unit, key);
    }

    /**
     * Gives every command sent through this proxy without an explicit key a key derived from
     * the consumer and the event being handled (see the class javadoc).
     *
     * @param consumerId          the consuming engine's consumer id (stable across restarts)
     * @param eventSequenceNumber the sequence number of the event being handled
     * @return this proxy
     */
    public GatewayTelemetryProxy withDerivedIdempotencyKeys(String consumerId, long eventSequenceNumber) {
        this.idempotencyScope = consumerId == null ? null : consumerId + ":" + eventSequenceNumber;
        this.commandOrdinal.set(0);
        return this;
    }

    /** Restarts the command ordinal: called before each attempt of a handler's retry loop. */
    public void beginAttempt() {
        commandOrdinal.set(0);
    }

    /**
     * {@link #beginAttempt()} on {@code gateway} if it is a proxy; a no-op for any other gateway
     * (a user-supplied one in a unit test, for example).
     */
    public static void beginAttempt(Object gateway) {
        if (gateway instanceof GatewayTelemetryProxy proxy) proxy.beginAttempt();
    }

    /**
     * {@code evento:<scope>:<ordinal>}, or, when that exceeds
     * {@link CommandMessage#MAX_IDEMPOTENCY_KEY_LENGTH} (a very long consumer id), its SHA-256
     * as {@code evento:sha256:<hex>}: same input, same key, always within the column width.
     */
    static String deriveIdempotencyKey(String scope, int ordinal) {
        var key = DERIVED_KEY_PREFIX + scope + ":" + ordinal;
        if (key.length() <= CommandMessage.MAX_IDEMPOTENCY_KEY_LENGTH) return key;
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return DERIVED_KEY_PREFIX + "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }




    @Override
    public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query) {
       return query(query, null, handledMessage);
    }

    @Override
    public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, long timeout, TimeUnit unit) {
        return query(query, null, handledMessage, timeout, unit);
    }

    @Override
    public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata) {
       return query(query, metadata, handledMessage);
    }


    @Override
    public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata, long timeout, TimeUnit unit) {
       return query(query, metadata, handledMessage, timeout, unit);
    }

    @Override
    public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata, Message<?> handledMessage) {
       return query(query, metadata, handledMessage, getDefaultTimeoutTime(), getDefaultTimeoutUnit());
    }


    @Override
    public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata, Message<?> handledMessage, long timeout, TimeUnit unit) {
        updateInvocationCounter(query);
        return queryGateway.query(query, tracingAgent.correlate(metadata, this.handledMessage), this.handledMessage, timeout, unit);
    }

    /**
     * Updates the invocation counter for a specific command or query.
     *
     * @param message The command or query being executed.
     */
    private void updateInvocationCounter(Payload message) {
        invocationCounter.putIfAbsent(message.getClass().getSimpleName(), new AtomicInteger());
        invocationCounter.get(message.getClass().getSimpleName()).incrementAndGet();
        if(message instanceof TrackablePayload c) {
            forceTelemetry = forceTelemetry || c.isForceTelemetry();
        }
    }

    /**
     * Sends the invocations metric to the performance service.
     */
    public void sendInvocationsMetric() {
        performanceService.sendInvocationsMetric(
                bundle,
                componentName,
                handledMessage,
                invocationCounter,
                instanceId,
                forceTelemetry
        );
    }

    /**
     * Sends the service time metric to the performance service and then sends the invocations metric.
     *
     * @param start The start time of the service operation.
     */
    public void sendServiceTimeMetric(Instant start) {
        performanceService.sendServiceTimeMetric(
                bundle,
                instanceId,
                componentName,
                handledMessage,
                start,
                forceTelemetry
        );
        sendInvocationsMetric();
    }
}
