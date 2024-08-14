package com.evento.application.consumer;

import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.ProjectorReference;
import com.evento.common.messaging.consumer.DeadPublishedEvent;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.utils.ProjectorStatus;
import com.evento.common.utils.Sleep;

import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Represents a consumer for projector events, responsible for processing and handling events
 * in a projector context.
 */
public class ProjectorEvenConsumer implements Runnable {

    private static final Logger logger = LogManager.getLogger(ProjectorEvenConsumer.class);

    // Fields for configuration and dependencies
    private final String bundleId;
    @Getter
    private final String projectorName;
    private final int projectorVersion;
    private final String context;
    private final Supplier<Boolean> isShuttingDown;
    private final ConsumerStateStore consumerStateStore;
    private final HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers;
    private final TracingAgent tracingAgent;
    private final BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy;
    private final int sssFetchSize;
    private final int sssFetchDelay;
    private final AtomicInteger alignmentCounter;
    private final Runnable onAllHeadReached;
    @Getter
    private final String consumerId;

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
     * @param onAllHeadReached            The runnable to execute when the head is reached.
     */
    public ProjectorEvenConsumer(String bundleId,
                                 String projectorName, int projectorVersion,
                                 String context, Supplier<Boolean> isShuttingDown,
                                 ConsumerStateStore consumerStateStore,
                                 HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers,
                                 TracingAgent tracingAgent, BiFunction<String, Message<?>,
            GatewayTelemetryProxy> gatewayTelemetryProxy, int sssFetchSize,
                                 int sssFetchDelay, AtomicInteger alignmentCounter,
                                 Runnable onAllHeadReached) {
        // Initialization of fields
        this.bundleId = bundleId;
        this.projectorName = projectorName;
        this.projectorVersion = projectorVersion;
        this.context = context;
        this.isShuttingDown = isShuttingDown;
        this.consumerStateStore = consumerStateStore;
        this.projectorMessageHandlers = projectorMessageHandlers;
        this.tracingAgent = tracingAgent;
        this.gatewayTelemetryProxy = gatewayTelemetryProxy;
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;
        this.alignmentCounter = alignmentCounter;
        this.onAllHeadReached = onAllHeadReached;

        // Construct consumer identifier
        this.consumerId = bundleId + "_" + projectorName + "_" + projectorVersion + "_" + context;
    }

    /**
     * Runs the projector event consumer, continuously processing and handling events
     * until the shutdown condition is met.
     */
    @Override
    public void run() {

        // Initialize projector status
        var ps = new ProjectorStatus();
        ps.setHeadReached(false);


        // Main loop for event processing
        while (!isShuttingDown.get()) {
            var hasError = false;
            var consumedEventCount = 0;

            try {
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
                                                ps
                                        );
                                        proxy.sendInvocationsMetric();
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
                logger.info("Event consumer head Reached for Projector: %s - Version: %d - Context: %s"
                        .formatted(projectorName, projectorVersion, context));
                var aligned = alignmentCounter.decrementAndGet();
                if (aligned == 0) {
                    onAllHeadReached.run();
                }
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
                                        ps
                                );
                                proxy.sendInvocationsMetric();
                                return null;
                            });

                }
        );
    }

    /**
     * Retrieves the dead published events from the dead event queue for the specified consumer.
     *
     * This method delegates the retrieval of events from the dead event queue to the consumer state store. It calls the {@code getEventsFromDeadEventQueue} method of the consumer
     *  state store, passing the consumer ID as a parameter. The method returns a Collection of DeadPublishedEvent objects representing the events from the dead event queue for the
     *  specified consumer.
     *
     * @return a Collection of DeadPublishedEvent objects representing the events from the dead event queue for the specified consumer
     * @throws Exception if an error occurs during the retrieval of events from the dead event queue
     *
     * @see DeadPublishedEvent
     * @see ConsumerStateStore
     */
    public Collection<DeadPublishedEvent> getDeadEventQueue() throws Exception {
        return consumerStateStore.getEventsFromDeadEventQueue(consumerId);
    }

    /**
     * Retrieves the last consumed event sequence number for a consumer.
     *
     * This method delegates the retrieval of the last event sequence number to the consumer state store.
     * It calls the {@code getLastEventSequenceNumberSagaOrHead} method of the consumer state store,
     * passing the consumer ID as a parameter. The method returns the last event sequence number
     * for the specified consumer.
     *
     * @return the last consumed event sequence number for the consumer
     * @throws Exception if an error occurs during the retrieval of the last event sequence number
     *
     * @see ConsumerStateStore#getLastEventSequenceNumberSagaOrHead(String)
     */
    public long getLastConsumedEvent() throws Exception {
        return consumerStateStore.getLastEventSequenceNumberSagaOrHead(consumerId);
    }

    /**
     * Sets the retry flag for a dead event of a specific consumer.
     *
     * @param eventSequenceNumber the sequence number of the dead event
     * @param retry               the retry flag, true if the event should be retried, false otherwise
     * @throws Exception if an error occurs during the retry flag setting
     */
    public void setDeadEventRetry(long eventSequenceNumber, boolean retry) throws Exception {
        consumerStateStore.setRetryDeadEvent(consumerId, eventSequenceNumber, retry);
    }

}
