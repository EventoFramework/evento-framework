package com.evento.application.consumer;

import com.evento.application.consumer.ConsumerHandle;
import com.evento.application.reference.ProjectorReference;
import com.evento.common.messaging.consumer.DeadPublishedEvent;
import com.evento.common.messaging.consumer.ConsumerProcessor;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.messaging.consumer.DeadEventQueue;
import com.evento.common.messaging.consumer.TransientConsumerException;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerFetchStatusResponseMessage;
import com.evento.common.utils.ChannelErrors;
import com.evento.common.utils.ProjectorStatus;
import com.evento.common.utils.Sleep;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * v2 replacement for {@code ProjectorEvenConsumer}.
 *
 * <p>The consume loop is identical to v1; the difference is that the underlying
 * persistence is the v2 SPI composition (lock + checkpoint + DLQ) wrapped by
 * {@link ConsumerProcessor}, rather than the v1 monolithic {@code ConsumerStateStore}
 * abstract class. The handler-dispatch lambda body is unchanged so the
 * observable behaviour for {@code @EventHandler} methods on projectors is
 * preserved one-for-one — slice 3.5 will then delete the v1 path.
 *
 * <p>Implements {@link ConsumerHandle} (the admin surface) so the v2 engines
 * slot into {@code BundleAdminRequestHandler.ConsumerLookup} alongside v1
 * {@code EventConsumer} instances during the migration window.
 */
public final class ProjectorEngine implements Runnable, ConsumerHandle {

    private static final Logger logger = LogManager.getLogger(ProjectorEngine.class);

    @Getter
    private final String bundleId;
    @Getter
    private final String projectorName;
    @Getter
    private final int projectorVersion;
    @Getter
    private final String context;
    @Getter
    private final String consumerId;

    private final Supplier<Boolean> isShuttingDown;
    private final ConsumerProcessor processor;
    private final ConsumerStateStore stateStore;
    private final DeadEventQueue deadEventQueue;
    private final HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers;
    private final DispatchContext dispatchContext;
    @Getter
    private final int sssFetchSize;
    @Getter
    private final int sssFetchDelay;
    private final AtomicInteger alignmentCounter;
    private final Runnable onAllHeadReached;
    private final ConsumerExecutorResolver.Routing routing;

    /**
     * How long the head-reached gate waits for async handlers to finish. Generous: it is
     * paid once per projector at start-up, and overrunning it means enabling the bundle
     * with an unaligned read model.
     */
    private static final Duration DRAIN_DEADLINE = Duration.ofMinutes(2);

    private volatile PublishedEvent lastConsumedEvent = null;

    public ProjectorEngine(String bundleId,
                           String projectorName,
                           int projectorVersion,
                           String context,
                           Supplier<Boolean> isShuttingDown,
                           ConsumerProcessor processor,
                           ConsumerStateStore stateStore,
                           DeadEventQueue deadEventQueue,
                           HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers,
                           DispatchContext dispatchContext,
                           int sssFetchSize,
                           int sssFetchDelay,
                           AtomicInteger alignmentCounter,
                           Runnable onAllHeadReached) {
        this(bundleId, projectorName, projectorVersion, context, isShuttingDown, processor,
                stateStore, deadEventQueue, projectorMessageHandlers, dispatchContext,
                sssFetchSize, sssFetchDelay, alignmentCounter, onAllHeadReached,
                ConsumerExecutorResolver.Routing.INLINE);
    }

    public ProjectorEngine(String bundleId,
                           String projectorName,
                           int projectorVersion,
                           String context,
                           Supplier<Boolean> isShuttingDown,
                           ConsumerProcessor processor,
                           ConsumerStateStore stateStore,
                           DeadEventQueue deadEventQueue,
                           HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers,
                           DispatchContext dispatchContext,
                           int sssFetchSize,
                           int sssFetchDelay,
                           AtomicInteger alignmentCounter,
                           Runnable onAllHeadReached,
                           ConsumerExecutorResolver.Routing routing) {
        this.routing = routing == null ? ConsumerExecutorResolver.Routing.INLINE : routing;
        this.bundleId = bundleId;
        this.projectorName = projectorName;
        this.projectorVersion = projectorVersion;
        this.context = context;
        this.consumerId = bundleId + "_" + projectorName + "_" + projectorVersion + "_" + context;
        this.isShuttingDown = isShuttingDown;
        this.processor = processor;
        this.stateStore = stateStore;
        this.deadEventQueue = deadEventQueue;
        this.projectorMessageHandlers = projectorMessageHandlers;
        this.dispatchContext = dispatchContext;
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;
        this.alignmentCounter = alignmentCounter;
        this.onAllHeadReached = onAllHeadReached;
    }

    @Override
    public void run() {
        var ps = new ProjectorStatus();
        ps.setHeadReached(false);
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
                    logger.debug("Fetching events for Projector: {} - Version: {} - Context: {} - Last Event: {}",
                            projectorName, projectorVersion, context, lastConsumedEvent);
                    consumedEventCount = processor.consumeEventsForProjector(
                            consumerId,
                            projectorName,
                            context,
                            publishedEvent -> dispatch(publishedEvent, ps),
                            sssFetchSize,
                            routing.resolver());
                }
            } catch (Throwable e) {
                // Exponential backoff for any transient failure (channel error OR
                // a downed/timing-out dependency surfaced as TransientConsumerException),
                // so a prolonged outage doesn't become a tight retry storm.
                isChannelError = ChannelErrors.isChannelError(e) || e instanceof TransientConsumerException;
                if (isChannelError) {
                    logger.warn("Transient error on projector consumer {} (attempt {}): {}",
                            consumerId, channelErrorAttempts + 1, e.getMessage());
                } else {
                    logger.error("Error on projector consumer: " + consumerId, e);
                }
                hasError = true;
            }

            // Async handlers fail on their own threads, long after the fetch loop moved
            // on, so their failures never surface as `hasError`. Left unchecked, a downed
            // dependency would be met by the loop pulling at full speed and dead-lettering
            // the entire stream — async events cannot be redelivered, their checkpoint has
            // already advanced. Back off on the same curve as a channel error instead.
            var asyncStreak = processor.asyncTransientFailureStreak(consumerId);

            if (hasError && isChannelError) {
                Sleep.apply(backoff.nextDelay(++channelErrorAttempts).toMillis());
            } else if (hasError) {
                Sleep.apply(sssFetchDelay);
            } else if (asyncStreak > 0) {
                var delay = backoff.nextDelay(asyncStreak).toMillis();
                logger.warn("Projector {} degraded: {} consecutive transient async failure(s), "
                                + "backing off {} ms", projectorName, asyncStreak, delay);
                Sleep.apply(delay);
            } else {
                channelErrorAttempts = 0;
                if (sssFetchSize - consumedEventCount > 10) {
                    Sleep.apply(sssFetchSize - consumedEventCount);
                }
            }

            if (!hasError && !ps.isHeadReached() && consumedEventCount >= 0 && consumedEventCount < sssFetchSize) {
                // For async handlers "consumed" means "started", so the read model is not
                // yet aligned when the fetch loop reaches head. Draining here is what stops
                // the bundle from being enabled — and serving queries — while writes are
                // still in flight.
                drainAsyncHandlers();
                ps.setHeadReached(true);
                logger.info("Projector head reached: {} - Version: {} - Context: {}",
                        projectorName, projectorVersion, context);
                var aligned = alignmentCounter.decrementAndGet();
                if (aligned == 0) {
                    onAllHeadReached.run();
                }
            }

            if (!ps.isHeadReached() && lastConsumedEvent != null) {
                var now = Instant.now().toEpochMilli();
                logger.info("Aligning to head Projector: {} - Version: {} - Context: {} - Last Event: {} - Now: {} - Diff: {}",
                        projectorName, projectorVersion, context, lastConsumedEvent, now,
                        now - lastConsumedEvent.getCreatedAt());
            }
        }
    }

    /**
     * Wait for every async handler this projector dispatched to finish.
     *
     * <p>Public so {@code EngineSupervisor} can drain before shutdown: in-flight events are
     * already checkpointed, so discarding them would lose them silently.
     *
     * @return {@code true} if the projector went idle within the deadline.
     */
    public boolean drainAsyncHandlers() {
        try {
            var drained = processor.awaitConsumerQuiescence(consumerId, DRAIN_DEADLINE);
            if (!drained) {
                logger.warn("Projector {} still has {} async handler(s) in flight after {}",
                        projectorName, processor.inFlightCount(consumerId), DRAIN_DEADLINE);
            }
            return drained;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Common per-event dispatch — shared between live consume + dead-event drain. */
    private void dispatch(PublishedEvent publishedEvent, ProjectorStatus ps) throws Throwable {
        var handlers = projectorMessageHandlers.get(publishedEvent.getEventName());
        if (handlers == null) return;
        var handler = handlers.getOrDefault(projectorName, null);
        if (handler == null) return;

        var proxy = dispatchContext.gatewayTelemetryProxy().apply(handler.getComponentName(), publishedEvent.getEventMessage());
        dispatchContext.tracingAgent().track(publishedEvent.getEventMessage(), handler.getComponentName(),
                null,
                () -> {
                    handler.invoke(
                            publishedEvent,
                            proxy,
                            proxy,
                            ps,
                            dispatchContext.messageHandlerInterceptor(),
                            t -> processor.handleLastError(consumerId, t));
                    proxy.sendInvocationsMetric();
                    lastConsumedEvent = publishedEvent;
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
        var ps = new ProjectorStatus();
        ps.setHeadReached(true);
        processor.consumeDeadEventsForProjector(
                consumerId,
                projectorName,
                publishedEvent -> dispatch(publishedEvent, ps));
    }
}
