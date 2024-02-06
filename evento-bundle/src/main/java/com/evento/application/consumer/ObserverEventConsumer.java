package com.evento.application.consumer;

import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.ObserverReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.utils.Sleep;

import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The ObserverEventConsumer class is responsible for consuming and processing events
 * for a specific observer.
 */
public class ObserverEventConsumer implements Runnable {
    private static final Logger logger = LogManager.getLogger(ObserverEventConsumer.class);

    // Fields for configuration and dependencies
    private final String bundleId;
    private final String observerName;
    private final int observerVersion;
    private final String context;
    private final Supplier<Boolean> isShuttingDown;
    private final ConsumerStateStore consumerStateStore;
    private final HashMap<String, HashMap<String, ObserverReference>> observerMessageHandlers;
    private final TracingAgent tracingAgent;
    private final BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy;
    private final int sssFetchSize;
    private final int sssFetchDelay;


    /**
     * ObserverEventConsumer is a class that represents a consumer for observing events.
     *
     * @param bundleId                The bundle id of the observer.
     * @param observerName            The name of the observer.
     * @param observerVersion         The version of the observer.
     * @param context                 The context of the observer.
     * @param isShuttingDown          A supplier that determines if the consumer is shutting down.
     * @param consumerStateStore      The state store for the consumer.
     * @param observerMessageHandlers The message handlers for the observer.
     * @param tracingAgent            The agent for tracing.
     * @param gatewayTelemetryProxy   The proxy for gateway telemetry.
     * @param sssFetchSize            The fetch size for the state store.
     * @param sssFetchDelay           The fetch delay for the state store.
     */
    public ObserverEventConsumer(String bundleId, String observerName, int observerVersion,
                                 String context, Supplier<Boolean> isShuttingDown,
                                 ConsumerStateStore consumerStateStore,
                                 HashMap<String, HashMap<String, ObserverReference>> observerMessageHandlers,
                                 TracingAgent tracingAgent, BiFunction<String, Message<?>,
            GatewayTelemetryProxy> gatewayTelemetryProxy,
                                 int sssFetchSize, int sssFetchDelay) {
        // Initialization of fields
        this.bundleId = bundleId;
        this.observerName = observerName;
        this.observerVersion = observerVersion;
        this.context = context;
        this.isShuttingDown = isShuttingDown;
        this.consumerStateStore = consumerStateStore;
        this.observerMessageHandlers = observerMessageHandlers;
        this.tracingAgent = tracingAgent;
        this.gatewayTelemetryProxy = gatewayTelemetryProxy;
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;
    }

    /**
     * Runs the saga event consumer, continuously processing and handling events
     * until the shutdown condition is met.
     */
    @Override
    public void run() {
        // Construct consumer identifier
        var consumerId = bundleId + "_" + observerName + "_" + observerVersion + "_" + context;

        // Main loop for event processing
        while (!isShuttingDown.get()) {
            var hasError = false;
            var consumedEventCount = 0;

            try {
                // Consume events from the state store and process them
                consumedEventCount = consumerStateStore.consumeEventsForObserver(consumerId,
                        observerName,
                        context, (publishedEvent) -> {
                            // Retrieve handlers for the event name
                            var handlers = observerMessageHandlers
                                    .get(publishedEvent.getEventName());
                            if (handlers == null) return;

                            // Retrieve the handler for the current saga
                            var handler = handlers.getOrDefault(observerName, null);
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
                                                publishedEvent.getEventMessage(),
                                                proxy,
                                                proxy
                                        );
                                        proxy.sendInvocationsMetric();
                                        return null;
                                    });
                        }, sssFetchSize);
            } catch (Throwable e) {
                logger.error("Error on observer consumer: " + consumerId, e);
                hasError = true;
            }

            // Sleep based on fetch size and error conditions
            if (sssFetchSize - consumedEventCount > 10) {
                Sleep.apply(hasError ? sssFetchDelay : sssFetchSize - consumedEventCount);
            }
        }
    }
}
