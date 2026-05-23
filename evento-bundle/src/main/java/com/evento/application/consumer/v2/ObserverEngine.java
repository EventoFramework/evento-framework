package com.evento.application.consumer.v2;

import com.evento.application.consumer.ConsumerHandle;
import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.application.performance.TracingAgent;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.application.reference.ObserverReference;
import com.evento.common.messaging.consumer.DeadPublishedEvent;
import com.evento.common.messaging.consumer.v2.ConsumerProcessor;
import com.evento.common.messaging.consumer.v2.ConsumerStateStore;
import com.evento.common.messaging.consumer.v2.DeadEventQueue;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerFetchStatusResponseMessage;
import com.evento.common.utils.Sleep;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * v2 replacement for {@code ObserverEventConsumer}.
 *
 * <p>Same shape as v1 (fetch → dispatch → sleep) but the consume cycle goes
 * through {@link ConsumerProcessor#consumeEventsForObserver}, which folds in
 * optional exactly-once dedup via the v2 {@code DedupeStore} SPI when one is
 * configured on the processor. The observer handler still runs at-least-once
 * by contract; dedup only changes whether it runs at-most-once.
 */
public final class ObserverEngine implements Runnable, ConsumerHandle {

    private static final Logger logger = LogManager.getLogger(ObserverEngine.class);

    @Getter
    private final String bundleId;
    @Getter
    private final String observerName;
    @Getter
    private final int observerVersion;
    @Getter
    private final String context;
    @Getter
    private final String consumerId;

    private final Supplier<Boolean> isShuttingDown;
    private final ConsumerProcessor processor;
    private final ConsumerStateStore stateStore;
    private final DeadEventQueue deadEventQueue;
    private final HashMap<String, HashMap<String, ObserverReference>> observerMessageHandlers;
    private final TracingAgent tracingAgent;
    private final BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy;
    @Getter
    private final int sssFetchSize;
    @Getter
    private final int sssFetchDelay;
    private final MessageHandlerInterceptor messageHandlerInterceptor;

    public ObserverEngine(String bundleId,
                          String observerName,
                          int observerVersion,
                          String context,
                          Supplier<Boolean> isShuttingDown,
                          ConsumerProcessor processor,
                          ConsumerStateStore stateStore,
                          DeadEventQueue deadEventQueue,
                          HashMap<String, HashMap<String, ObserverReference>> observerMessageHandlers,
                          TracingAgent tracingAgent,
                          BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy,
                          int sssFetchSize,
                          int sssFetchDelay,
                          MessageHandlerInterceptor messageHandlerInterceptor) {
        this.bundleId = bundleId;
        this.observerName = observerName;
        this.observerVersion = observerVersion;
        this.context = context;
        this.consumerId = bundleId + "_" + observerName + "_" + observerVersion + "_" + context;
        this.isShuttingDown = isShuttingDown;
        this.processor = processor;
        this.stateStore = stateStore;
        this.deadEventQueue = deadEventQueue;
        this.observerMessageHandlers = observerMessageHandlers;
        this.tracingAgent = tracingAgent;
        this.gatewayTelemetryProxy = gatewayTelemetryProxy;
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;
        this.messageHandlerInterceptor = messageHandlerInterceptor;
    }

    @Override
    public void run() {
        while (!isShuttingDown.get()) {
            var hasError = false;
            var consumedEventCount = 0;

            try {
                if (stateStore.isEnabled(consumerId)) {
                    consumedEventCount = processor.consumeEventsForObserver(
                            consumerId,
                            observerName,
                            context,
                            this::dispatch,
                            sssFetchSize);
                }
            } catch (Throwable e) {
                logger.error("Error on observer consumer: " + consumerId, e);
                hasError = true;
            }

            if (sssFetchSize - consumedEventCount > 10) {
                Sleep.apply(hasError ? sssFetchDelay : sssFetchSize - consumedEventCount);
            }
        }
    }

    private void dispatch(PublishedEvent publishedEvent) throws Throwable {
        var handlers = observerMessageHandlers.get(publishedEvent.getEventName());
        if (handlers == null) return;
        var handler = handlers.getOrDefault(observerName, null);
        if (handler == null) return;

        var proxy = gatewayTelemetryProxy.apply(handler.getComponentName(), publishedEvent.getEventMessage());
        tracingAgent.track(publishedEvent.getEventMessage(), handler.getComponentName(),
                null,
                () -> {
                    handler.invoke(
                            publishedEvent,
                            proxy,
                            proxy,
                            messageHandlerInterceptor,
                            t -> processor.handleLastError(consumerId, t));
                    proxy.sendInvocationsMetric();
                    return null;
                });
    }

    // -- ConsumerHandle ------------------------------------------------------

    @Override
    public ConsumerFetchStatusResponseMessage toConsumerStatus() {
        return processor.toConsumerStatus(consumerId);
    }

    @Override
    public long getLastConsumedEvent() throws Exception {
        return processor.getLastEventSequenceNumberSagaOrHead(consumerId);
    }

    @Override
    public Collection<DeadPublishedEvent> getDeadEventQueue() {
        return deadEventQueue.getAll(consumerId);
    }

    @Override
    public void setDeadEventRetry(long eventSequenceNumber, boolean retry) {
        deadEventQueue.setRetry(consumerId, eventSequenceNumber, retry);
    }

    @Override
    public void deleteDeadEvent(long eventSequenceNumber) {
        deadEventQueue.remove(consumerId, eventSequenceNumber);
    }

    @Override
    public void consumeDeadEventQueue() throws Exception {
        processor.consumeDeadEventsForObserver(consumerId, observerName, this::dispatch);
    }
}
