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
import java.util.concurrent.ConcurrentHashMap;
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
 * explicit key gets {@code evento:<consumerId>:<eventSequenceNumber>:<CommandSimpleName>:<n>},
 * {@code n} counting the commands <em>of that type</em> sent in this handler invocation from 0.
 * A redelivered event (the consumer failed or crashed before its checkpoint, a dead event
 * retried, the handler's own retry loop) therefore re-issues the same keys, and the broker
 * answers the commands that already ran with their stored events instead of running them again.
 * The ordinals are reset by {@link #beginAttempt()} before each attempt of the handler's retry
 * loop.
 *
 * <p>Ordinals are counted per command type so that an attempt which sends fewer commands (a
 * branch that depends on a query answer, say) can only shift keys among commands of one type,
 * never hand one command type the key of another; the broker records the command type and
 * aggregate id with each key and refuses a key presented by a different command
 * ({@code IdempotencyKeyMismatchException}) instead of replaying the other command's event.
 * Deterministic as long as the handler sends the commands of one type in a deterministic order;
 * a handler that fans commands out from several threads should pass explicit keys instead.
 *
 * <p>Keys are derived only when the target gateway {@linkplain CommandGateway#supportsIdempotencyKeys()
 * supports them}; a gateway that does not (a decorator written before keys existed) gets the
 * plain five-argument send, as before G5.
 */
public class GatewayTelemetryProxy implements CommandGateway, QueryGateway {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    private final String bundle;
    private final String instanceId;
    private final String componentName;

    private final PerformanceService performanceService;

    private final HashMap<String, AtomicInteger> invocationCounter = new HashMap<>();

    /** For a command handler: events of each declared type the handled command produced (3.0.1). */
    private final java.util.LinkedHashMap<String, Integer> producedEvents = new java.util.LinkedHashMap<>();

    private final Message<?> handledMessage;
    private final TracingAgent tracingAgent;

    private boolean forceTelemetry = false;

    /** {@code consumerId:eventSequenceNumber} when commands get derived keys, else {@code null}. */
    private volatile String idempotencyScope;
    /** Per command type (simple name): the ordinal of the next command of that type. */
    private final ConcurrentHashMap<String, AtomicInteger> commandOrdinals = new ConcurrentHashMap<>();

    /** Prefix of every derived key; caller-chosen keys may not use it. */
    static final String DERIVED_KEY_PREFIX = CommandMessage.RESERVED_IDEMPOTENCY_KEY_PREFIX;

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
     * scoped to a consumed event and the target gateway supports keys, else without a key
     * through the target's five-argument {@code send}. Every command takes an ordinal of its
     * type, keyed or not, so an explicit key on one command does not shift the derived keys of
     * the others. An explicit key in the reserved {@code evento:} namespace fails the send.
     */
    @Override
    public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
                                         long timeout, TimeUnit unit, String idempotencyKey) {
        updateInvocationCounter(command);
        var type = commandType(command);
        int ordinal = commandOrdinals.computeIfAbsent(type, t -> new AtomicInteger()).getAndIncrement();
        var key = CommandMessage.normalizeIdempotencyKey(idempotencyKey);
        if (key != null) {
            try {
                CommandMessage.checkCallerIdempotencyKey(key);
            } catch (IllegalArgumentException e) {
                return CompletableFuture.failedFuture(e);
            }
            // Explicit: the target carries it or fails loudly (never dropped silently).
            return tracingAgent.traceSend(command, metadata, this.handledMessage, correlated ->
                    commandGateway.send(command, correlated, this.handledMessage, timeout, unit, key));
        }
        var scope = idempotencyScope;
        if (scope != null && commandGateway.supportsIdempotencyKeys()) {
            var derived = deriveIdempotencyKey(scope, type, ordinal);
            return tracingAgent.traceSend(command, metadata, this.handledMessage, correlated ->
                    commandGateway.send(command, correlated, this.handledMessage, timeout, unit, derived));
        }
        return tracingAgent.traceSend(command, metadata, this.handledMessage, correlated ->
                commandGateway.send(command, correlated, this.handledMessage, timeout, unit));
    }

    /**
     * {@link #send(Command, Metadata, Message, long, TimeUnit, String)} reporting where the
     * event was stored: same counters, correlation and (derived) idempotency key.
     */
    @Override
    public <R> CompletableFuture<com.evento.common.messaging.gateway.Tracked<R>> sendTracked(
            Command command, Metadata metadata, Message<?> handledMessage, long timeout, TimeUnit unit,
            String idempotencyKey) {
        updateInvocationCounter(command);
        var type = commandType(command);
        int ordinal = commandOrdinals.computeIfAbsent(type, t -> new AtomicInteger()).getAndIncrement();
        var key = CommandMessage.normalizeIdempotencyKey(idempotencyKey);
        if (key != null) {
            try {
                CommandMessage.checkCallerIdempotencyKey(key);
            } catch (IllegalArgumentException e) {
                return CompletableFuture.failedFuture(e);
            }
        } else if (idempotencyScope != null && commandGateway.supportsIdempotencyKeys()) {
            key = deriveIdempotencyKey(idempotencyScope, type, ordinal);
        }
        var sentKey = key;
        return tracingAgent.traceSend(command, metadata, this.handledMessage, correlated ->
                commandGateway.sendTracked(command, correlated, this.handledMessage, timeout, unit, sentKey));
    }

    /** Whether the target gateway carries keys (see {@link CommandGateway#supportsIdempotencyKeys()}). */
    @Override
    public boolean supportsIdempotencyKeys() {
        return commandGateway != null && commandGateway.supportsIdempotencyKeys();
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
        this.commandOrdinals.clear();
        return this;
    }

    /** Restarts the command ordinals: called before each attempt of a handler's retry loop. */
    public void beginAttempt() {
        commandOrdinals.clear();
    }

    /**
     * {@link #beginAttempt()} on {@code gateway} if it is a proxy; a no-op for any other gateway
     * (a user-supplied one in a unit test, for example).
     */
    public static void beginAttempt(Object gateway) {
        if (gateway instanceof GatewayTelemetryProxy proxy) proxy.beginAttempt();
    }

    /** The simple name of the command's class (the full name for an anonymous or local class). */
    static String commandType(Command command) {
        var simple = command.getClass().getSimpleName();
        return simple.isEmpty() ? command.getClass().getName() : simple;
    }

    /**
     * {@code evento:<scope>:<commandType>:<ordinal>}, or, when that exceeds
     * {@link CommandMessage#MAX_IDEMPOTENCY_KEY_LENGTH} (a very long consumer id or class name),
     * its SHA-256 as {@code evento:sha256:<hex>}: same input, same key, always within the column
     * width.
     */
    static String deriveIdempotencyKey(String scope, String commandType, int ordinal) {
        var key = DERIVED_KEY_PREFIX + scope + ":" + commandType + ":" + ordinal;
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
        return tracingAgent.traceSend(query, metadata, this.handledMessage, correlated ->
                queryGateway.query(query, correlated, this.handledMessage, timeout, unit));
    }

    @Override
    public <T extends QueryResponse<?>> CompletableFuture<T> queryWith(Query<T> query,
                                                                      com.evento.common.messaging.gateway.QueryOptions options) {
        updateInvocationCounter(query);
        var opts = options == null ? com.evento.common.messaging.gateway.QueryOptions.defaults() : options;
        return tracingAgent.traceSend(query, opts.getMetadata(), this.handledMessage, correlated ->
                queryGateway.queryWith(query, opts.metadata(correlated)));
    }

    @Override
    public <V extends com.evento.common.modeling.messaging.payload.View>
    com.evento.common.messaging.gateway.QueryStream<V> stream(Query<? extends QueryResponse<V>> query,
                                                              com.evento.common.messaging.gateway.QueryOptions options) {
        updateInvocationCounter(query);
        return queryGateway.stream(query, correlated(options));
    }

    private com.evento.common.messaging.gateway.QueryOptions correlated(
            com.evento.common.messaging.gateway.QueryOptions options) {
        var opts = options == null ? com.evento.common.messaging.gateway.QueryOptions.defaults() : options;
        return opts.metadata(tracingAgent.correlate(opts.getMetadata(), this.handledMessage));
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
     * Records what the handled command produced, for a command handler (3.0.1): every type it
     * declares, with 0 for those it did not produce this time. The broker averages them into the
     * probability (or mean count) the performance model gives each event's edge.
     *
     * @param declared the simple names of the event types the handler declares
     * @param produced the simple names of the events it produced, one per event
     */
    public void recordProducedEvents(java.util.Collection<String> declared, java.util.Collection<String> produced) {
        for (var name : declared) producedEvents.putIfAbsent(name, 0);
        for (var name : produced) producedEvents.merge(name, 1, Integer::sum);
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
                producedEvents,
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
