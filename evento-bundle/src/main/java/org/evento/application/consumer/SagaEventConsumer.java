package org.evento.application.consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.ProjectorReference;
import org.evento.application.reference.SagaReference;
import org.evento.common.messaging.consumer.ConsumerStateStore;
import org.evento.common.modeling.annotations.handler.SagaEventHandler;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.common.modeling.messaging.message.application.Message;

import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class SagaEventConsumer implements Runnable {
    private final Logger logger = LogManager.getLogger(SagaEventConsumer.class);

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

    public SagaEventConsumer(String bundleId, String sagaName, int sagaVersion, String context, Supplier<Boolean> isShuttingDown, ConsumerStateStore consumerStateStore, HashMap<String, HashMap<String, SagaReference>> sagaMessageHandlers, TracingAgent tracingAgent, BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy, int sssFetchSize, int sssFetchDelay) {
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

    @Override
    public void run() {
        var consumerId = bundleId + "_" + sagaName + "_" + sagaVersion + "_" + context;
        while (!isShuttingDown.get()) {
            var hasError = false;
            var consumedEventCount = 0;
            try {
                consumedEventCount = consumerStateStore.consumeEventsForSaga(consumerId,
                        sagaName,
                        context, (sagaStateFetcher, publishedEvent) -> {
                            var handlers = sagaMessageHandlers
                                    .get(publishedEvent.getEventName());
                            if (handlers == null) return null;

                            var handler = handlers.getOrDefault(sagaName, null);
                            if (handler == null) return null;

                            var associationProperty = handler.getSagaEventHandler(publishedEvent.getEventName())
                                    .getAnnotation(SagaEventHandler.class).associationProperty();
                            var associationValue = publishedEvent.getEventMessage().getAssociationValue(associationProperty);

                            var sagaState = sagaStateFetcher.getLastState(
                                    sagaName,
                                    associationProperty,
                                    associationValue
                            );
                            var proxy = gatewayTelemetryProxy.apply(handler.getComponentName(),
                                    publishedEvent.getEventMessage());
                            return tracingAgent.track(publishedEvent.getEventMessage(), handler.getComponentName(),
                                    null,
                                    () -> {
                                        var resp = handler.invoke(
                                                publishedEvent.getEventMessage(),
                                                sagaState,
                                                proxy,
                                                proxy
                                        );
                                        proxy.sendInvocationsMetric();
                                        return resp;
                                    });
                        }, sssFetchSize);
            } catch (Throwable e) {
                logger.error("Error on saga consumer: " + consumerId, e);
                hasError = true;
            }
            if (sssFetchSize - consumedEventCount > 10) {
                try {
                    Thread.sleep(hasError ? sssFetchDelay : sssFetchSize - consumedEventCount);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
