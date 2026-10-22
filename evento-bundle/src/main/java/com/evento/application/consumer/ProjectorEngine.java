package com.evento.application.consumer;

import com.evento.application.consumer.ConsumerHandle;
import com.evento.application.reference.ProjectorReference;
import com.evento.common.messaging.consumer.CheckpointMode;
import com.evento.common.messaging.consumer.DeadPublishedEvent;
import com.evento.common.messaging.consumer.ConsumerProcessor;
import com.evento.common.messaging.consumer.ProjectorCheckpoint;
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
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

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

    private static final Logger logger = LoggerFactory.getLogger(ProjectorEngine.class);

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
    @Getter
    private final CheckpointMode checkpointMode;
    @Getter
    private final boolean waitForHeadReached;

    /**
     * How long the head-reached gate waits for async handlers to finish. Generous: it is
     * paid once per projector at start-up, and overrunning it means enabling the bundle
     * with an unaligned read model.
     */
    private static final Duration DRAIN_DEADLINE = Duration.ofMinutes(2);

    private volatile PublishedEvent lastConsumedEvent = null;

    // ---- read-your-writes (3.0) ----
    /** Signalled after every consume cycle: a query waiting for a position re-checks. */
    private final java.util.concurrent.locks.ReentrantLock progressLock = new java.util.concurrent.locks.ReentrantLock();
    private final java.util.concurrent.locks.Condition progressed = progressLock.newCondition();
    /** Released by a waiting query: the loop fetches now instead of finishing its idle pause. */
    private final java.util.concurrent.Semaphore wakeup = new java.util.concurrent.Semaphore(0);
    /** How often waiting queries may read the store (another instance may hold this lane). */
    private static final long STORE_READ_INTERVAL_NANOS = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(50);
    private final java.util.concurrent.atomic.AtomicLong storeReadAtNanos = new java.util.concurrent.atomic.AtomicLong();

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
                ConsumerExecutorResolver.Routing.INLINE, CheckpointMode.ON_START);
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
        this(bundleId, projectorName, projectorVersion, context, isShuttingDown, processor,
                stateStore, deadEventQueue, projectorMessageHandlers, dispatchContext,
                sssFetchSize, sssFetchDelay, alignmentCounter, onAllHeadReached,
                routing, CheckpointMode.ON_START);
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
                           ConsumerExecutorResolver.Routing routing,
                           CheckpointMode checkpointMode) {
        this(bundleId, projectorName, projectorVersion, context, isShuttingDown, processor,
                stateStore, deadEventQueue, projectorMessageHandlers, dispatchContext,
                sssFetchSize, sssFetchDelay, alignmentCounter, onAllHeadReached,
                routing, checkpointMode, true);
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
                           ConsumerExecutorResolver.Routing routing,
                           CheckpointMode checkpointMode,
                           boolean waitForHeadReached) {
        this.routing = routing == null ? ConsumerExecutorResolver.Routing.INLINE : routing;
        this.checkpointMode = checkpointMode == null ? CheckpointMode.ON_START : checkpointMode;
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
        this.waitForHeadReached = waitForHeadReached;
    }

    @Override
    public void run() {
        var handled = HeadSignal.handledBy(projectorMessageHandlers, projectorName);
        dispatchContext.headSignal().register(wakeup, handled);
        var ps = new ProjectorStatus();
        ps.setHeadReached(false);
        // Knows its events: the checkpoint of one it does not handle is written with the next.
        var consumer = com.evento.common.messaging.consumer.EventConsumer.of(
                publishedEvent -> dispatch(publishedEvent, ps), handled);
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
                            consumer,
                            sssFetchSize,
                            routing.resolver(),
                            checkpointMode);
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
            // Before any pause: a query waiting to read its own write re-checks now.
            signalProgress();

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
                if (consumedEventCount < 0) {
                    // The lock is busy: no notice comes when it is free again, so poll for it.
                    idle(sssFetchSize - consumedEventCount);
                } else if (sssFetchSize - consumedEventCount > 10) {
                    dispatchContext.headSignal().pauseAtHead(wakeup, sssFetchSize - consumedEventCount, sssFetchDelay);
                }
            }

            if (!hasError && !ps.isHeadReached() && consumedEventCount >= 0 && consumedEventCount < sssFetchSize) {
                // For async handlers "consumed" means "started", so the read model is not
                // yet aligned when the fetch loop reaches head. Draining here is what stops
                // a gating projector from releasing the bundle-enable gate — and serving
                // queries — while writes are still in flight.
                drainAsyncHandlers();
                ps.setHeadReached(true);
                logger.info("Projector head reached: {} - Version: {} - Context: {}",
                        projectorName, projectorVersion, context);
                if (waitForHeadReached) {
                    var aligned = alignmentCounter.decrementAndGet();
                    if (aligned == 0) {
                        onAllHeadReached.run();
                    }
                }
            }

            if (!ps.isHeadReached() && lastConsumedEvent != null) {
                var now = Instant.now().toEpochMilli();
                logger.info("Aligning to head Projector: {} - Version: {} - Context: {} - Last Event: {} - Now: {} - Diff: {}",
                        projectorName, projectorVersion, context, lastConsumedEvent, now,
                        now - lastConsumedEvent.getCreatedAt());
            }
        }
        dispatchContext.headSignal().unregister(wakeup);
    }

    /** The pause between two fetches at the head, cut short by {@link #wakeup}. */
    private void idle(long millis) {
        try {
            if (wakeup.tryAcquire(millis, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                wakeup.drainPermits();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void signalProgress() {
        progressLock.lock();
        try {
            progressed.signalAll();
        } finally {
            progressLock.unlock();
        }
    }

    /**
     * Blocks until this projector has processed every event up to {@code sequenceNumber}
     * of its context (read-your-writes), or {@code deadlineNanos} ({@link System#nanoTime()})
     * passes.
     *
     * <p>"Processed" is the checkpoint: the one this process committed, or the one in the
     * store when another instance holds this lane. Under {@link CheckpointMode#ON_START} an
     * async handler's checkpoint moves when the handler starts, so the position counts only
     * while no async handler is running; under {@link CheckpointMode#WATERMARK} it is the
     * completed frontier. A waiting query wakes the consume loop, so the answer comes after
     * one fetch rather than after the idle pause.
     *
     * @return how long it waited, in nanoseconds
     * @throws com.evento.common.modeling.exceptions.ConsistencyTimeoutException when the deadline passes first
     */
    public long awaitPosition(long sequenceNumber, long deadlineNanos) throws InterruptedException {
        long start = System.nanoTime();
        while (!reached(sequenceNumber)) {
            long now = System.nanoTime();
            if (now - deadlineNanos >= 0) {
                throw new com.evento.common.modeling.exceptions.ConsistencyTimeoutException(
                        "projector " + projectorName + " (context " + context + ") has processed up to "
                                + Math.max(processor.knownCheckpoint(consumerId), 0) + ", not yet event "
                                + sequenceNumber + ", within the query's deadline");
            }
            wakeup.release();
            progressLock.lock();
            try {
                progressed.awaitNanos(Math.min(deadlineNanos - now, STORE_READ_INTERVAL_NANOS));
            } finally {
                progressLock.unlock();
            }
        }
        return System.nanoTime() - start;
    }

    private boolean reached(long sequenceNumber) {
        boolean quiescent = checkpointMode == CheckpointMode.WATERMARK || processor.inFlightCount(consumerId) == 0;
        if (!quiescent) return false;
        if (processor.knownCheckpoint(consumerId) >= sequenceNumber) return true;
        // Not known here: another instance may be consuming this lane. Read the store,
        // at most once per interval whatever the number of waiting queries.
        long now = System.nanoTime();
        long last = storeReadAtNanos.get();
        if (now - last >= STORE_READ_INTERVAL_NANOS && storeReadAtNanos.compareAndSet(last, now)) {
            try {
                return processor.storedCheckpoint(consumerId) >= sequenceNumber;
            } catch (RuntimeException e) {
                logger.debug("Checkpoint read for {} failed while a query waited: {}", consumerId, e.toString());
            }
        }
        return false;
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
            if (checkpointMode == CheckpointMode.WATERMARK) {
                // Persist what just finished rather than leaving it for the next cycle:
                // on a graceful stop there is no next cycle, and the batch would replay.
                processor.flushWatermark(consumerId, projectorName, ProjectorCheckpoint::new);
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

        var proxy = dispatchContext.consumerGateway(handler.getComponentName(), publishedEvent, consumerId);
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
