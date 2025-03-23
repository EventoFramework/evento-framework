package com.evento.application.consumer;

import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.ObserverReference;
import lombok.Getter;
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
public class ObserverEventConsumer extends EventConsumer {

    // Fields for configuration and dependencies
    @Getter
    private final String bundleId;
    @Getter
    private final String observerName;
    @Getter
    private final int observerVersion;
    @Getter
    private final String context;
    private final Supplier<Boolean> isShuttingDown;
    private final HashMap<String, HashMap<String, ObserverReference>> observerMessageHandlers;
    private final TracingAgent tracingAgent;
    private final BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy;
    @Getter
    private final int sssFetchSize;
    @Getter
    private final int sssFetchDelay;
    private final MessageHandlerInterceptor messageHandlerInterceptor;


    /**
     * Constructs an ObserverEventConsumer with the specified parameters.
     *
     * @param bundleId               The unique identifier for the bundle.
     * @param observerName           The name of the observer.
     * @param observerVersion        The version of the observer.
     * @param context                The context under which the consumer operates.
     * @param isShuttingDown         A supplier providing the shutdown condition.
     * @param consumerStateStore     A state store for tracking the consumer's state.
     * @param observerMessageHandlers A map of observer message handlers grouped by message type and handler name.
     * @param tracingAgent           The tracing agent used for distributed tracing.
     * @param gatewayTelemetryProxy  A function to create a telemetry proxy for messages and gateway.
     * @param sssFetchSize           The fetch size for processing events.
     * @param sssFetchDelay          The delay in milliseconds between fetch operations.
     * @param messageHandlerInterceptor An interceptor for handling and modifying messages during processing.
     */
    public ObserverEventConsumer(String bundleId, String observerName, int observerVersion,
                                 String context, Supplier<Boolean> isShuttingDown,
                                 ConsumerStateStore consumerStateStore,
                                 HashMap<String, HashMap<String, ObserverReference>> observerMessageHandlers,
                                 TracingAgent tracingAgent, BiFunction<String, Message<?>,
            GatewayTelemetryProxy> gatewayTelemetryProxy,
                                 int sssFetchSize, int sssFetchDelay, MessageHandlerInterceptor messageHandlerInterceptor) {
        super(bundleId + "_" + observerName + "_" + observerVersion + "_" + context, consumerStateStore);
        // Initialization of fields
        this.bundleId = bundleId;
        this.observerName = observerName;
        this.observerVersion = observerVersion;
        this.context = context;
        this.isShuttingDown = isShuttingDown;
        this.observerMessageHandlers = observerMessageHandlers;
        this.tracingAgent = tracingAgent;
        this.gatewayTelemetryProxy = gatewayTelemetryProxy;
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;
        this.messageHandlerInterceptor = messageHandlerInterceptor;
    }

    /**
     * Runs the saga event consumer, continuously processing and handling events
     * until the shutdown condition is met.
     */
    @Override
    public void run() {

        // Main loop for event processing
        while (!isShuttingDown.get()) {
            var hasError = false;
            var consumedEventCount = 0;

            try {
                // Consume events from the state store and process them
                consumedEventCount = consumerStateStore.consumeEventsForObserver(consumerId,
                        observerName,
                        context,
                        (publishedEvent) -> {
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
                                                publishedEvent,
                                                proxy,
                                                proxy,
                                                messageHandlerInterceptor
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

    public void consumeDeadEventQueue() throws Exception {


        consumerStateStore.consumeDeadEventsForObserver(
                consumerId,
                observerName,
                (publishedEvent) -> {
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
                                        publishedEvent,
                                        proxy,
                                        proxy,
                                        messageHandlerInterceptor);
                                proxy.sendInvocationsMetric();
                                return null;
                            });
                }
        );
    }


}
