package org.evento.application.proxy;

import org.evento.application.performance.TracingAgent;
import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.QueryGateway;
import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.messaging.message.application.Metadata;
import org.evento.common.modeling.messaging.payload.Command;
import org.evento.common.modeling.messaging.payload.Payload;
import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.QueryResponse;
import org.evento.common.performance.PerformanceService;

import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The GatewayTelemetryProxy class acts as a proxy for CommandGateway and QueryGateway,
 * adding telemetry and performance tracking functionality.
 */
public class GatewayTelemetryProxy implements CommandGateway, QueryGateway {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    private final String bundle;
    private final String componentName;

    private final PerformanceService performanceService;

    private final HashMap<String, AtomicInteger> invocationCounter = new HashMap<>();

    private final Message<?> handledMessage;
    private final TracingAgent tracingAgent;

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
     */
    public GatewayTelemetryProxy(CommandGateway commandGateway,
                                 QueryGateway queryGateway,
                                 String bundle, PerformanceService performanceService,
                                 String componentName,
                                 Message<?> handledMessage,
                                 TracingAgent tracingAgent) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
        this.bundle = bundle;
        this.performanceService = performanceService;
        this.componentName = componentName;
        this.handledMessage = handledMessage;
        this.tracingAgent = tracingAgent;
    }

    @Override
    public <R> R sendAndWait(Command command) {
        try {
            return commandGateway.sendAndWait(command, tracingAgent.correlate((Metadata) null, this.handledMessage));
        } finally {
            updateInvocationCounter(command);
        }
    }

    @Override
    public <R> R sendAndWait(Command command, long timeout, TimeUnit unit) {
        try {
            return commandGateway.sendAndWait(command, tracingAgent.correlate((Metadata) null, this.handledMessage), timeout, unit);
        } finally {
            updateInvocationCounter(command);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> CompletableFuture<R> send(Command command) {
        return (CompletableFuture<R>) commandGateway.send(command, tracingAgent.correlate((Metadata) null, this.handledMessage))
                .whenComplete((s, e) -> updateInvocationCounter(command));
    }

    @Override
    public <R> R sendAndWait(Command command, Metadata metadata) {
        try {
            return commandGateway.sendAndWait(command, tracingAgent.correlate(metadata, this.handledMessage));
        } finally {
            updateInvocationCounter(command);
        }
    }

    @Override
    public <R> R sendAndWait(Command command, Metadata metadata, long timeout, TimeUnit unit) {
        try {
            return commandGateway.sendAndWait(command, tracingAgent.correlate(metadata, this.handledMessage), timeout, unit);
        } finally {
            updateInvocationCounter(command);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> CompletableFuture<R> send(Command command, Metadata metadata) {
        return (CompletableFuture<R>) commandGateway.send(command, tracingAgent.correlate(metadata, this.handledMessage))
                .whenComplete((s, e) -> updateInvocationCounter(command));
    }

    @Override
    public <R> R sendAndWait(Command command, Metadata metadata, Message<?> handledMessage) {
        try {
            return commandGateway.sendAndWait(command, tracingAgent.correlate(metadata, this.handledMessage), this.handledMessage);
        } finally {
            updateInvocationCounter(command);
        }
    }

    @Override
    public <R> R sendAndWait(Command command, Metadata metadata, Message<?> handledMessage, long timeout, TimeUnit unit) {
        try {
            return commandGateway.sendAndWait(command, tracingAgent.correlate(metadata, this.handledMessage), this.handledMessage, timeout, unit);
        } finally {
            updateInvocationCounter(command);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage) {
        return (CompletableFuture<R>) commandGateway.send(command, tracingAgent.correlate(metadata, this.handledMessage),
                this.handledMessage).whenComplete((s, e) -> updateInvocationCounter(command));
    }

    @Override
    public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata, Message<?> handledMessage) {
        return queryGateway.query(query, tracingAgent.correlate(metadata, this.handledMessage), this.handledMessage)
                .whenComplete((s, e) -> updateInvocationCounter(query));
    }

    @Override
    public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query) {
        return queryGateway.query(query, tracingAgent.correlate((Metadata) null, this.handledMessage))
                .whenComplete((s, e) -> updateInvocationCounter(query));
    }

    @Override
    public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata) {
        return queryGateway.query(query, tracingAgent.correlate(metadata, this.handledMessage))
                .whenComplete((s, e) -> updateInvocationCounter(query));
    }

    /**
     * Updates the invocation counter for a specific command or query.
     *
     * @param message The command or query being executed.
     */
    private void updateInvocationCounter(Payload message) {
        invocationCounter.putIfAbsent(message.getClass().getSimpleName(), new AtomicInteger());
        invocationCounter.get(message.getClass().getSimpleName()).incrementAndGet();
    }

    /**
     * Sends the invocations metric to the performance service.
     */
    public void sendInvocationsMetric() {
        performanceService.sendInvocationsMetric(
                bundle,
                componentName,
                handledMessage,
                invocationCounter
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
                componentName,
                handledMessage,
                start
        );
        sendInvocationsMetric();
    }
}
