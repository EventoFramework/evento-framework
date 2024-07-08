package com.evento.application.consumer;

import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.SagaReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.utils.Sleep;

import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Represents a consumer for saga events, responsible for processing and handling events
 * in a saga context.
 */
public class SagaEventConsumer implements Runnable {
    private static final Logger logger = LogManager.getLogger(SagaEventConsumer.class);

    // Fields for configuration and dependencies
    private final String bundleId;
    private final String sagaName;
    private final int sagaVersion;
    private final String context;
    private final Supplier<Boolean> isShuttingDown;
    private final ConsumerStateStore consumerStateStore;
    private final HashMap<String, HashMap<String, SagaReference>> sagaMessageHandlers;
    private final TracingAgent tracingAgent;
    private final BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy;
    private final int sssFetchSize;
    private final int sssFetchDelay;

    /**
     * Constructs a new SagaEventConsumer with the specified parameters.
     *
     * @param bundleId              The bundle identifier.
     * @param sagaName              The name of the saga.
     * @param sagaVersion           The version of the saga.
     * @param context               The context in which the saga operates.
     * @param isShuttingDown        A supplier indicating whether the consumer is shutting down.
     * @param consumerStateStore    The state store for tracking consumer state.
     * @param sagaMessageHandlers   The map of event names to saga handlers.
     * @param tracingAgent          The tracing agent for tracking events.
     * @param gatewayTelemetryProxy The function for creating a telemetry proxy for a gateway.
     * @param sssFetchSize          The fetch size for consuming events from the state store.
     * @param sssFetchDelay         The delay for fetching events from the state store.
     */
    public SagaEventConsumer(String bundleId, String sagaName, int sagaVersion,
                             String context, Supplier<Boolean> isShuttingDown,
                             ConsumerStateStore consumerStateStore,
                             HashMap<String, HashMap<String, SagaReference>> sagaMessageHandlers,
                             TracingAgent tracingAgent, BiFunction<String, Message<?>,
            GatewayTelemetryProxy> gatewayTelemetryProxy,
                             int sssFetchSize, int sssFetchDelay) {
        // Initialization of fields
        this.bundleId = bundleId;
        this.sagaName = sagaName;
        this.sagaVersion = sagaVersion;
        this.context = context;
        this.isShuttingDown = isShuttingDown;
        this.consumerStateStore = consumerStateStore;
        this.sagaMessageHandlers = sagaMessageHandlers;
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
        var consumerId = bundleId + "_" + sagaName + "_" + sagaVersion + "_" + context;

        // Main loop for event processing
        while (!isShuttingDown.get()) {
            var hasError = false;
            var consumedEventCount = 0;

            try {
                // Consume events from the state store and process them
                consumedEventCount = consumerStateStore.consumeEventsForSaga(consumerId,
                        sagaName,
                        context, (sagaStateFetcher, publishedEvent) -> {
                            // Retrieve handlers for the event name
                            var handlers = sagaMessageHandlers
                                    .get(publishedEvent.getEventName());
                            if (handlers == null) return null;

                            // Retrieve the handler for the current saga
                            var handler = handlers.getOrDefault(sagaName, null);
                            if (handler == null) return null;

                            // Extract information from SagaEventHandler annotation
                            var a = handler.getSagaEventHandler(publishedEvent.getEventName())
                                    .getAnnotation(SagaEventHandler.class);
                            var associationProperty = a.associationProperty();
                            var isInit = a.init();
                            var associationValue = publishedEvent.getEventMessage().getAssociationValue(associationProperty);


                            // Retrieve the last state from the saga state fetcher
                            var sagaState = sagaStateFetcher.getLastState(
                                    sagaName,
                                    associationProperty,
                                    associationValue
                            );

                            // Check for initialization condition
                            if (sagaState == null && !isInit) {
                                return null;
                            }

                            // Create telemetry proxy for the gateway
                            var proxy = gatewayTelemetryProxy.apply(handler.getComponentName(),
                                    publishedEvent.getEventMessage());

                            // Track the event using the tracing agent
                            return tracingAgent.track(publishedEvent.getEventMessage(), handler.getComponentName(),
                                    null,
                                    () -> {
                                        // Invoke the handler and send telemetry metrics
                                        var resp = handler.invoke(
                                                publishedEvent.getEventMessage(),
                                                sagaState,
                                                proxy,
                                                proxy
                                        );
                                        proxy.sendInvocationsMetric();
                                        return resp == null ? sagaState : resp;
                                    });
                        }, sssFetchSize);
            } catch (Throwable e) {
                logger.error("Error on saga consumer: " + consumerId, e);
                hasError = true;
            }

            // Sleep based on fetch size and error conditions
            if (sssFetchSize - consumedEventCount > 10) {
                Sleep.apply(hasError ? sssFetchDelay : sssFetchSize - consumedEventCount);
            }
        }
    }
}
