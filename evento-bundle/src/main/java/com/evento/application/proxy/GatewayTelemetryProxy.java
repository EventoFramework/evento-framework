package com.evento.application.proxy;

import com.evento.application.performance.TracingAgent;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.messaging.payload.Payload;
import com.evento.common.modeling.messaging.payload.TrackablePayload;
import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.QueryResponse;
import com.evento.common.performance.PerformanceService;

import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The GatewayTelemetryProxy class acts as a proxy for CommandGateway and QueryGateway,
 * adding telemetry and performance tracking functionality.
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
        updateInvocationCounter(command);
        return commandGateway.send(command, tracingAgent.correlate(metadata, this.handledMessage),
                this.handledMessage, timeout, unit);
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
