package com.evento.application.consumer.v2;

import com.evento.application.consumer.ConsumerHandle;
import com.evento.application.reference.SagaReference;
import com.evento.common.messaging.consumer.DeadPublishedEvent;
import com.evento.common.messaging.consumer.SagaEventConsumer;
import com.evento.common.messaging.consumer.v2.ConsumerProcessor;
import com.evento.common.messaging.consumer.v2.ConsumerStateStore;
import com.evento.common.messaging.consumer.v2.DeadEventQueue;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerFetchStatusResponseMessage;
import com.evento.common.utils.Sleep;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.function.Supplier;

/**
 * v2 replacement for {@code SagaEventConsumer} (engine class, not the
 * {@code com.evento.common.messaging.consumer.SagaEventConsumer} functional
 * interface — that's the per-event callback shape, which we still implement
 * inline here).
 *
 * <p>Behaviour matches v1: per-event handler looks up the saga state by
 * association via the fetcher injected by {@link ConsumerProcessor}, invokes
 * {@code @SagaEventHandler}, and returns the next state to persist (or null
 * to keep the previous one — v2 processor handles the persist/delete/insert
 * decision based on whether the state is {@code ended} and whether a saga id
 * was found).
 */
public final class SagaEngine implements Runnable, ConsumerHandle {

    private static final Logger logger = LogManager.getLogger(SagaEngine.class);

    @Getter
    private final String bundleId;
    @Getter
    private final String sagaName;
    @Getter
    private final int sagaVersion;
    @Getter
    private final String context;
    @Getter
    private final String consumerId;

    private final Supplier<Boolean> isShuttingDown;
    private final ConsumerProcessor processor;
    private final ConsumerStateStore stateStore;
    private final DeadEventQueue deadEventQueue;
    private final HashMap<String, HashMap<String, SagaReference>> sagaMessageHandlers;
    private final DispatchContext dispatchContext;
    @Getter
    private final int sssFetchSize;
    @Getter
    private final int sssFetchDelay;

    public SagaEngine(String bundleId,
                      String sagaName,
                      int sagaVersion,
                      String context,
                      Supplier<Boolean> isShuttingDown,
                      ConsumerProcessor processor,
                      ConsumerStateStore stateStore,
                      DeadEventQueue deadEventQueue,
                      HashMap<String, HashMap<String, SagaReference>> sagaMessageHandlers,
                      DispatchContext dispatchContext,
                      int sssFetchSize,
                      int sssFetchDelay) {
        this.bundleId = bundleId;
        this.sagaName = sagaName;
        this.sagaVersion = sagaVersion;
        this.context = context;
        this.consumerId = bundleId + "_" + sagaName + "_" + sagaVersion + "_" + context;
        this.isShuttingDown = isShuttingDown;
        this.processor = processor;
        this.stateStore = stateStore;
        this.deadEventQueue = deadEventQueue;
        this.sagaMessageHandlers = sagaMessageHandlers;
        this.dispatchContext = dispatchContext;
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;
    }

    @Override
    public void run() {
        SagaEventConsumer perEvent = this::dispatch;
        while (!isShuttingDown.get()) {
            var hasError = false;
            var consumedEventCount = 0;

            try {
                if (stateStore.isEnabled(consumerId)) {
                    consumedEventCount = processor.consumeEventsForSaga(
                            consumerId,
                            sagaName,
                            context,
                            perEvent,
                            sssFetchSize);
                }
            } catch (Throwable e) {
                logger.error("Error on saga consumer: " + consumerId, e);
                hasError = true;
            }

            if (sssFetchSize - consumedEventCount > 10) {
                Sleep.apply(hasError ? sssFetchDelay : sssFetchSize - consumedEventCount);
            }
        }
    }

    /** Per-event saga dispatch — same body for live + dead-event paths. */
    private com.evento.common.modeling.state.SagaState dispatch(
            com.evento.common.messaging.consumer.SagaStateFetcher sagaStateFetcher,
            com.evento.common.modeling.messaging.dto.PublishedEvent publishedEvent) throws Throwable {

        var handlers = sagaMessageHandlers.get(publishedEvent.getEventName());
        if (handlers == null) return null;
        var handler = handlers.getOrDefault(sagaName, null);
        if (handler == null) return null;

        var a = handler.getSagaEventHandler(publishedEvent.getEventName())
                .getAnnotation(SagaEventHandler.class);
        var associationProperty = a.associationProperty();
        var isInit = a.init();
        var associationValue = publishedEvent.getEventMessage().getAssociationValue(associationProperty);

        var sagaState = sagaStateFetcher.getLastState(sagaName, associationProperty, associationValue);
        if (sagaState == null && !isInit) {
            return null;
        }

        var proxy = dispatchContext.gatewayTelemetryProxy().apply(handler.getComponentName(), publishedEvent.getEventMessage());

        final var stateForLambda = sagaState;
        return dispatchContext.tracingAgent().track(publishedEvent.getEventMessage(), handler.getComponentName(),
                null,
                () -> {
                    var resp = handler.invoke(
                            publishedEvent,
                            stateForLambda,
                            proxy,
                            proxy,
                            dispatchContext.messageHandlerInterceptor(),
                            t -> processor.handleLastError(consumerId, t));
                    proxy.sendInvocationsMetric();
                    return resp == null ? stateForLambda : resp;
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
        processor.consumeDeadEventsForSaga(consumerId, sagaName, this::dispatch);
    }
}
