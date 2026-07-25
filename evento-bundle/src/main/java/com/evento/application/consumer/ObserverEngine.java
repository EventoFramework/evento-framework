package com.evento.application.consumer;

import com.evento.application.consumer.ConsumerHandle;
import com.evento.application.reference.ObserverReference;
import com.evento.common.messaging.consumer.DeadPublishedEvent;
import com.evento.common.messaging.consumer.ConsumerProcessor;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.messaging.consumer.DeadEventQueue;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerFetchStatusResponseMessage;
import com.evento.common.utils.ChannelErrors;
import com.evento.common.utils.Sleep;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
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
    private final DispatchContext dispatchContext;
    @Getter
    private final int sssFetchSize;
    @Getter
    private final int sssFetchDelay;

    private final ConsumerExecutorResolver.Routing routing;

    private static final Duration DRAIN_DEADLINE = Duration.ofMinutes(2);

    public ObserverEngine(String bundleId,
                          String observerName,
                          int observerVersion,
                          String context,
                          Supplier<Boolean> isShuttingDown,
                          ConsumerProcessor processor,
                          ConsumerStateStore stateStore,
                          DeadEventQueue deadEventQueue,
                          HashMap<String, HashMap<String, ObserverReference>> observerMessageHandlers,
                          DispatchContext dispatchContext,
                          int sssFetchSize,
                          int sssFetchDelay) {
        this(bundleId, observerName, observerVersion, context, isShuttingDown, processor,
                stateStore, deadEventQueue, observerMessageHandlers, dispatchContext,
                sssFetchSize, sssFetchDelay, ConsumerExecutorResolver.Routing.INLINE);
    }

    public ObserverEngine(String bundleId,
                          String observerName,
                          int observerVersion,
                          String context,
                          Supplier<Boolean> isShuttingDown,
                          ConsumerProcessor processor,
                          ConsumerStateStore stateStore,
                          DeadEventQueue deadEventQueue,
                          HashMap<String, HashMap<String, ObserverReference>> observerMessageHandlers,
                          DispatchContext dispatchContext,
                          int sssFetchSize,
                          int sssFetchDelay,
                          ConsumerExecutorResolver.Routing routing) {
        this.routing = routing == null ? ConsumerExecutorResolver.Routing.INLINE : routing;
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
        this.dispatchContext = dispatchContext;
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;
    }

    @Override
    public void run() {
        var backoff = new ExponentialBackoffWithJitter(
                Duration.ofMillis(sssFetchDelay), Duration.ofSeconds(30), 0.2,
                ExponentialBackoffWithJitter.UNBOUNDED);
        int channelErrorAttempts = 0;
        while (!isShuttingDown.get()) {
            var hasError = false;
            var isChannelError = false;
            var consumedEventCount = 0;

            try {
                if (stateStore.isEnabled(consumerId)) {
                    consumedEventCount = processor.consumeEventsForObserver(
                            consumerId,
                            observerName,
                            context,
                            this::dispatch,
                            sssFetchSize,
                            routing.resolver());
                }
            } catch (Throwable e) {
                isChannelError = ChannelErrors.isChannelError(e);
                if (isChannelError) {
                    logger.warn("Channel error on observer consumer {} (attempt {}): {}",
                            consumerId, channelErrorAttempts + 1, e.getMessage());
                } else {
                    logger.error("Error on observer consumer: " + consumerId, e);
                }
                hasError = true;
            }

            // See ProjectorEngine: async failures never surface as `hasError`, so without
            // this a downed dependency would be fed the whole stream at full speed.
            var asyncStreak = processor.asyncTransientFailureStreak(consumerId);

            if (hasError && isChannelError) {
                Sleep.apply(backoff.nextDelay(++channelErrorAttempts).toMillis());
            } else if (hasError) {
                Sleep.apply(sssFetchDelay);
            } else if (asyncStreak > 0) {
                var delay = backoff.nextDelay(asyncStreak).toMillis();
                logger.warn("Observer {} degraded: {} consecutive transient async failure(s), "
                                + "backing off {} ms", observerName, asyncStreak, delay);
                Sleep.apply(delay);
            } else {
                channelErrorAttempts = 0;
                if (sssFetchSize - consumedEventCount > 10) {
                    Sleep.apply(sssFetchSize - consumedEventCount);
                }
            }
        }
    }

    /**
     * Wait for every async handler this observer dispatched to finish. Called by
     * {@code EngineSupervisor} before shutdown — in-flight events are already
     * checkpointed, so discarding them would lose them silently.
     */
    public boolean drainAsyncHandlers() {
        try {
            var drained = processor.awaitConsumerQuiescence(consumerId, DRAIN_DEADLINE);
            if (!drained) {
                logger.warn("Observer {} still has {} async handler(s) in flight after {}",
                        observerName, processor.inFlightCount(consumerId), DRAIN_DEADLINE);
            }
            return drained;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void dispatch(PublishedEvent publishedEvent) throws Throwable {
        var handlers = observerMessageHandlers.get(publishedEvent.getEventName());
        if (handlers == null) return;
        var handler = handlers.getOrDefault(observerName, null);
        if (handler == null) return;

        var proxy = dispatchContext.gatewayTelemetryProxy().apply(handler.getComponentName(), publishedEvent.getEventMessage());
        dispatchContext.tracingAgent().track(publishedEvent.getEventMessage(), handler.getComponentName(),
                null,
                () -> {
                    handler.invoke(
                            publishedEvent,
                            proxy,
                            proxy,
                            dispatchContext.messageHandlerInterceptor(),
                            t -> processor.handleLastError(consumerId, t));
                    proxy.sendInvocationsMetric();
                    return null;
                });
    }

    // -- ConsumerHandle ------------------------------------------------------

    @Override
    public ConsumerFetchStatusResponseMessage toConsumerStatus() {
        var status = processor.toConsumerStatus(consumerId);
        status.setAsyncExecutors(routing.executorNames());
        return status;
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
