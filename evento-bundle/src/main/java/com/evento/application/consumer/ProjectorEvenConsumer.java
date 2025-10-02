package com.evento.application.consumer;

import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.ProjectorReference;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.utils.ProjectorStatus;
import com.evento.common.utils.Sleep;

import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Represents a consumer for projector events, responsible for processing and handling events
 * in a projector context.
 */
public class ProjectorEvenConsumer extends EventConsumer {

    private static final Logger logger = LogManager.getLogger(ProjectorEvenConsumer.class);

    // Fields for configuration and dependencies
    @Getter
    private final String bundleId;
    @Getter
    private final String projectorName;
    @Getter
    private final int projectorVersion;
    @Getter
    private final String context;
    private final Supplier<Boolean> isShuttingDown;
    private final HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers;
    private final TracingAgent tracingAgent;
    private final BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy;
    @Getter
    private final int sssFetchSize;
    @Getter
    private final int sssFetchDelay;
    private final AtomicInteger alignmentCounter;
    private final Runnable onAllHeadReached;
    private final MessageHandlerInterceptor messageHandlerInterceptor;

    private PublishedEvent lastConsumedEvent = null;

    /**
     * Constructs a new ProjectorEvenConsumer with the specified parameters.
     *
     * @param bundleId                 The bundle identifier.
     * @param projectorName            The name of the projector.
     * @param projectorVersion         The version of the projector.
     * @param context                  The context in which the projector operates.
     * @param isShuttingDown           A supplier indicating whether the consumer is shutting down.
     * @param consumerStateStore       The state store for tracking consumer state.
     * @param projectorMessageHandlers The map of event names to projector handlers.
     * @param tracingAgent             The tracing agent for tracking events.
     * @param gatewayTelemetryProxy    The function for creating a telemetry proxy for a gateway.
     * @param sssFetchSize             The fetch size for consuming events from the state store.
     * @param sssFetchDelay            The delay for fetching events from the state store.
     * @param alignmentCounter         The atomic counter for tracking alignment.
     * @param onAllHeadReached         The runnable to execute when the head is reached.
     * @param messageHandlerInterceptor       The message interceptor
     */
    public ProjectorEvenConsumer(String bundleId,
                                 String projectorName, int projectorVersion,
                                 String context, Supplier<Boolean> isShuttingDown,
                                 ConsumerStateStore consumerStateStore,
                                 HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers,
                                 TracingAgent tracingAgent, BiFunction<String, Message<?>,
            GatewayTelemetryProxy> gatewayTelemetryProxy, int sssFetchSize,
                                 int sssFetchDelay, AtomicInteger alignmentCounter,
                                 Runnable onAllHeadReached, MessageHandlerInterceptor messageHandlerInterceptor) {
        super(bundleId + "_" + projectorName + "_" + projectorVersion + "_" + context, consumerStateStore);
        // Initialization of fields
        this.bundleId = bundleId;
        this.projectorName = projectorName;
        this.projectorVersion = projectorVersion;
        this.context = context;
        this.isShuttingDown = isShuttingDown;
        this.projectorMessageHandlers = projectorMessageHandlers;
        this.tracingAgent = tracingAgent;
        this.gatewayTelemetryProxy = gatewayTelemetryProxy;
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;
        this.alignmentCounter = alignmentCounter;
        this.onAllHeadReached = onAllHeadReached;
        this.messageHandlerInterceptor = messageHandlerInterceptor;
    }

    /**
     * Runs the projector event consumer, continuously processing and handling events
     * until the shutdown condition is met.
     */
    @Override
    public void run() {


        var consumptionStart = Instant.now().toEpochMilli();
        // Initialize projector status
        var ps = new ProjectorStatus();
        ps.setHeadReached(false);
        // Main loop for event processing
        while (!isShuttingDown.get()) {
            var hasError = false;
            var consumedEventCount = 0;

            try {
                logger.debug("Fetching events for Projector: {} - Version: {} - Context: {} - Last Event: {}",
                        projectorName, projectorVersion, context, lastConsumedEvent);
                // Consume events from the state store and process them
                consumedEventCount = consumerStateStore.consumeEventsForProjector(
                        consumerId,
                        projectorName,
                        context,
                        publishedEvent -> {
                            // Retrieve handlers for the event name
                            var handlers = projectorMessageHandlers
                                    .get(publishedEvent.getEventName());
                            if (handlers == null) return;

                            // Retrieve the handler for the current projector
                            var handler = handlers.getOrDefault(projectorName, null);
                            if (handler == null) return;

                            // Create telemetry proxy for the gateway
                            var proxy = gatewayTelemetryProxy.apply(handler.getComponentName(),
                                    publishedEvent.getEventMessage());

                            // Track the event using the tracing agent
                            tracingAgent.track(publishedEvent.getEventMessage(), handler.getComponentName(),
                                    null,
                                    () -> {
                                        // Invoke the handler and send telemetry metrics
                                        handler.invoke(
                                                publishedEvent,
                                                proxy,
                                                proxy,
                                                ps,
                                                messageHandlerInterceptor
                                        );
                                        proxy.sendInvocationsMetric();
                                        lastConsumedEvent = publishedEvent;
                                        return null;
                                    });

                        }, sssFetchSize);
            } catch (Throwable e) {
                logger.error("Error on projector consumer: " + consumerId, e);
                hasError = true;
            }

            // Sleep based on fetch size and error conditions
            if (sssFetchSize - consumedEventCount > 10) {
                Sleep.apply(hasError ? sssFetchDelay : sssFetchSize - consumedEventCount);
            }

            // Check for head reached condition and execute onHeadReached if necessary
            if (!hasError && !ps.isHeadReached() && consumedEventCount >= 0 && consumedEventCount < sssFetchSize) {
                ps.setHeadReached(true);
                logger.info("Projector head reached: {} - Version: {} - Context: {}"
                        ,projectorName, projectorVersion, context);
                var aligned = alignmentCounter.decrementAndGet();
                if (aligned == 0) {
                    onAllHeadReached.run();
                }
            }

            if(!ps.isHeadReached() && lastConsumedEvent != null) {
                var now = Instant.now().toEpochMilli();
                logger.info("Aligning to head Projector: {} - Version: {} - Context: {} - Last Event: {} - Now: {} - Diff: {}"
                            ,projectorName, projectorVersion, context, lastConsumedEvent, now, now - lastConsumedEvent.getCreatedAt());


            }
        }
    }

    public void consumeDeadEventQueue() throws Exception {

        // Initialize projector status
        var ps = new ProjectorStatus();
        ps.setHeadReached(true);

        consumerStateStore.consumeDeadEventsForProjector(
                consumerId,
                projectorName,
                publishedEvent -> {
                    // Retrieve handlers for the event name
                    var handlers = projectorMessageHandlers
                            .get(publishedEvent.getEventName());
                    if (handlers == null) return;

                    // Retrieve the handler for the current projector
                    var handler = handlers.getOrDefault(projectorName, null);
                    if (handler == null) return;

                    // Create telemetry proxy for the gateway
                    var proxy = gatewayTelemetryProxy.apply(handler.getComponentName(),
                            publishedEvent.getEventMessage());

                    // Track the event using the tracing agent
                    tracingAgent.track(publishedEvent.getEventMessage(), handler.getComponentName(),
                            null,
                            () -> {
                                // Invoke the handler and send telemetry metrics
                                handler.invoke(
                                        publishedEvent,
                                        proxy,
                                        proxy,
                                        ps,
                                        messageHandlerInterceptor);
                                proxy.sendInvocationsMetric();
                                return null;
                            });

                }
        );
    }

}
