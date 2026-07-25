package com.evento.common.messaging.consumer;

import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.consumer.ConsumerDisabledException;
import com.evento.common.messaging.consumer.EventConsumer;
import com.evento.common.messaging.consumer.EventFetchRequest;
import com.evento.common.messaging.consumer.EventFetchResponse;
import com.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
import com.evento.common.messaging.consumer.EventLastSequenceNumberResponse;
import com.evento.common.messaging.consumer.SagaEventConsumer;
import com.evento.common.messaging.consumer.SagaStateFetcher;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerFetchStatusResponseMessage;
import com.evento.common.modeling.state.SagaState;
import com.evento.common.performance.PerformanceService;
import com.evento.common.utils.ChannelErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * v2 replacement for the v1 {@code ConsumerStateStore} abstract class.
 *
 * <p>Where v1 was one ~700-line abstract class mixing persistence, lock,
 * saga lookup, dead-letter queue and the consume loop — and four sub-classes
 * duplicating every method — v2 splits the persistence concerns across
 * focused SPIs and centralises the consume loop logic here:
 *
 * <ul>
 *   <li>{@link ConsumerLock} — the cross-instance exclusive zone. Always
 *       taken with {@code try (var ignored = lock.tryAcquire(consumerId)) {…}}.</li>
 *   <li>{@link ConsumerStateStore} — checkpoint with optimistic versioning +
 *       enabled flag + error history.</li>
 *   <li>{@link SagaStateStore} — saga instance lookup by association.</li>
 *   <li>{@link DeadEventQueue} — per-consumer dead-letter queue.</li>
 *   <li>{@link DedupeStore} — optional, for observer-style exactly-once.</li>
 * </ul>
 *
 * <p>The processor itself holds no state. All correctness comes from the
 * lock (only one instance processes a given consumer at a time) plus
 * optimistic versioning on the checkpoint (defence in depth if a lock leaks).
 */
public final class ConsumerProcessor {

    private static final Logger logger = LogManager.getLogger(ConsumerProcessor.class);

    private final EventoServer eventoServer;
    private final ConsumerLock lock;
    private final ConsumerStateStore stateStore;
    private final SagaStateStore sagaStateStore;
    private final DeadEventQueue deadEventQueue;
    private final DedupeStore dedupeStore; // nullable — only used by observer at-least-once
    private final PerformanceService performanceService;
    private final ObjectMapper objectMapper;
    private final ConsumerExecutor observerExecutor;
    private final long timeoutMillis;
    private final Duration submitTimeout;
    private final Duration inlineBarrierTimeout;

    /**
     * Per-consumer count of tasks dispatched to a {@link ConsumerExecutor} and not yet
     * finished. Tracked here rather than on the executor because executors are shared by
     * name across consumers: draining <em>this</em> consumer (the projector head-reached
     * gate) must not wait on another consumer's work.
     */
    private final Map<String, InFlightTracker> inFlight = new ConcurrentHashMap<>();

    private ConsumerProcessor(Builder b) {
        this.eventoServer = b.eventoServer;
        this.lock = b.lock;
        this.stateStore = b.stateStore;
        this.sagaStateStore = b.sagaStateStore;
        this.deadEventQueue = b.deadEventQueue;
        this.dedupeStore = b.dedupeStore;
        this.performanceService = b.performanceService;
        this.objectMapper = b.objectMapper;
        this.observerExecutor = b.observerExecutor;
        this.timeoutMillis = b.timeoutMillis;
        this.submitTimeout = b.submitTimeout;
        this.inlineBarrierTimeout = b.inlineBarrierTimeout;
    }

    public static Builder builder() { return new Builder(); }

    // --- Consume loops ------------------------------------------------------

    /**
     * Projector consume cycle. Returns the number of events processed, or
     * {@code -1} if the exclusive lock could not be taken (another instance is
     * already running the consumer).
     */
    public int consumeEventsForProjector(String consumerId,
                                         String projectorName,
                                         String context,
                                         EventConsumer projectorEventConsumer,
                                         int fetchSize) throws Throwable {
        return consumeEventsForProjector(consumerId, projectorName, context,
                projectorEventConsumer, fetchSize, null);
    }

    /**
     * Projector consume cycle with optional parallel dispatch.
     *
     * <p>{@code executorResolver} maps an event to the {@link ConsumerExecutor} its handler
     * declared via {@code @EventHandler(executor = "...")}, or {@code null} for the inline
     * (sequential) path. A {@code null} resolver makes every event inline, which is exactly
     * the behaviour of the 5-argument overload.
     *
     * <p>For an async event the checkpoint advances once the task has <b>started</b>, not
     * once it has finished. That is what bounds how far the consumer may run ahead of
     * completion: with a capacity-{@code P} executor, at most {@code P} events are
     * checkpointed-but-unfinished at any moment. If the executor has no capacity within the
     * submit timeout the cycle returns early — the checkpoint stands at the last started
     * event, the consumer lock is released (important: a JDBC lock pins a pooled connection
     * for the whole cycle), and the next cycle re-fetches from there.
     */
    public int consumeEventsForProjector(String consumerId,
                                         String projectorName,
                                         String context,
                                         EventConsumer projectorEventConsumer,
                                         int fetchSize,
                                         Function<PublishedEvent, ConsumerExecutor> executorResolver) throws Throwable {
        Optional<ConsumerLock.LockHandle> held = lock.tryAcquire(consumerId);
        if (held.isEmpty()) return -1;
        try (var ignored = held.get()) {
            var cursor = readCursor(consumerId);
            long lastSeq = cursor.checkpoint instanceof EventCheckpoint e ? e.lastSequenceNumber()
                    : cursor.checkpoint instanceof ProjectorCheckpoint p ? p.lastSequenceNumber()
                    : cursor.checkpoint instanceof SagaCheckpoint s ? s.lastSequenceNumber()
                    : 0L;
            var resp = fetchEvents(context, lastSeq, fetchSize, projectorName);

            int consumed = 0;
            long currentVersion = cursor.version;
            for (PublishedEvent event : resp.getEvents()) {
                var start = Instant.now();
                var executor = executorResolver == null ? null : executorResolver.apply(event);

                if (executor != null) {
                    if (!submitAsync(consumerId, projectorName, event, projectorEventConsumer, executor)) {
                        // Executor saturated: stop here rather than block holding the
                        // consumer lock. Everything up to the previous event is
                        // checkpointed; this one is re-fetched next cycle.
                        logger.debug("Executor '{}' saturated for projector {} at seq {} — ending cycle",
                                executor.name(), projectorName, event.getEventSequenceNumber());
                        return consumed;
                    }
                    currentVersion = advanceCheckpoint(consumerId,
                            new ProjectorCheckpoint(event.getEventSequenceNumber()), currentVersion);
                    consumed++;
                    recordMetric(projectorName, event, start);
                    continue;
                }

                // Inline handlers act as a barrier over earlier async events on the same
                // consumer: without this, a sequential handler could observe state from
                // before an earlier event was applied — a read-your-writes violation that
                // is very hard to diagnose.
                awaitInlineBarrier(consumerId, projectorName);

                try {
                    projectorEventConsumer.consume(event);
                } catch (ConsumerDisabledException e) {
                    logger.warn("Event ignored due to consumer disabled for projector {} and event {}.",
                            projectorName, event.getEventName());
                    return consumed;
                } catch (Throwable e) {
                    if (isTransient(e)) {
                        // Connectivity/timeout failure (e.g. a downed dependency or
                        // a mid-burst crash): do NOT advance the checkpoint — leave
                        // the event for redelivery once the dependency recovers.
                        // Propagate as a typed transient signal so the engine loop
                        // backs off exponentially and retries from the same
                        // checkpoint instead of losing the event to the DLQ.
                        logger.warn("Transient failure for projector {} on event {} (seq {}) — "
                                        + "not advancing checkpoint, will redeliver: {}",
                                projectorName, event.getEventName(), event.getEventSequenceNumber(), rootMessage(e));
                        throw new TransientConsumerException(rootMessage(e), e);
                    }
                    deadEventQueue.add(consumerId, event, e);
                    logger.error("Event consumption error for projector {} event {} — moved to DLQ",
                            projectorName, event.getEventName(), e);
                }
                currentVersion = advanceCheckpoint(consumerId, new ProjectorCheckpoint(event.getEventSequenceNumber()), currentVersion);
                consumed++;
                recordMetric(projectorName, event, start);
            }
            return consumed;
        }
    }

    public void consumeDeadEventsForProjector(String consumerId,
                                              String projectorName,
                                              EventConsumer projectorEventConsumer) throws Exception {
        Optional<ConsumerLock.LockHandle> held = lock.tryAcquire(consumerId);
        if (held.isEmpty()) return;
        try (var ignored = held.get()) {
            for (PublishedEvent event : deadEventQueue.getRetriable(consumerId)) {
                var start = Instant.now();
                try {
                    deadEventQueue.remove(consumerId, event);
                    projectorEventConsumer.consume(event);
                } catch (ConsumerDisabledException e) {
                    logger.warn("Dead event ignored due to consumer disabled for projector {} and event {}.",
                            projectorName, event.getEventName());
                    return;
                } catch (Throwable e) {
                    deadEventQueue.add(consumerId, event, e);
                    logger.error("Dead-event reprocess failed for projector {} event {} — kept in DLQ",
                            projectorName, event.getEventName(), e);
                }
                recordMetric(projectorName, event, start);
            }
        }
    }

    public int consumeEventsForObserver(String consumerId,
                                        String observerName,
                                        String context,
                                        EventConsumer observerEventConsumer,
                                        int fetchSize) throws Throwable {
        return consumeEventsForObserver(consumerId, observerName, context,
                observerEventConsumer, fetchSize, null);
    }

    /**
     * Observer consume cycle. Observers have always dispatched asynchronously and
     * checkpointed on submit; the difference now is that dispatch goes through a
     * {@link ConsumerExecutor}, so the fan-out is bounded and applies backpressure.
     *
     * <p>{@code executorResolver} selects a per-handler executor; events whose handler
     * names none fall back to the processor's observer executor.
     */
    public int consumeEventsForObserver(String consumerId,
                                        String observerName,
                                        String context,
                                        EventConsumer observerEventConsumer,
                                        int fetchSize,
                                        Function<PublishedEvent, ConsumerExecutor> executorResolver) throws Throwable {
        Optional<ConsumerLock.LockHandle> held = lock.tryAcquire(consumerId);
        if (held.isEmpty()) return -1;
        try (var ignored = held.get()) {
            var cursor = readCursor(consumerId);
            long lastSeq = cursor.checkpoint == null
                    ? fetchHeadAndSeed(consumerId, EventCheckpoint::new)
                    : cursor.checkpoint.lastSequenceNumber();
            // Re-read version after possible head-seed commit
            long currentVersion = cursor.checkpoint == null
                    ? stateStore.read(consumerId).map(VersionedCheckpoint::version).orElse(cursor.version)
                    : cursor.version;
            var resp = fetchEvents(context, lastSeq, fetchSize, observerName);

            int consumed = 0;
            for (PublishedEvent event : resp.getEvents()) {
                var start = Instant.now();
                final String eventId = String.valueOf(event.getEventSequenceNumber());
                final boolean shouldRun = dedupeStore == null || dedupeStore.tryClaim(consumerId, eventId);
                if (shouldRun) {
                    var executor = executorResolver == null ? null : executorResolver.apply(event);
                    if (executor == null) executor = observerExecutor;
                    boolean started = submitAsync(consumerId, executor, () -> {
                        try {
                            observerEventConsumer.consume(event);
                        } catch (Throwable e) {
                            if (dedupeStore != null) {
                                dedupeStore.release(consumerId, eventId);
                            }
                            try {
                                deadEventQueue.add(consumerId, event, e);
                                logger.error("Event consumption error for observer {} event {} — moved to DLQ",
                                        observerName, event.getEventName(), e);
                            } catch (Throwable ignored2) {
                                logger.error("DLQ insert failed for observer {} event {} — will be ignored",
                                        observerName, event.getEventName(), ignored2);
                            }
                        }
                    });
                    if (!started) {
                        // Saturated. Release the dedupe claim we just took, otherwise this
                        // event would be permanently skipped when the next cycle re-fetches
                        // it — the claim would already be held by nobody running.
                        if (dedupeStore != null) dedupeStore.release(consumerId, eventId);
                        logger.debug("Executor '{}' saturated for observer {} at seq {} — ending cycle",
                                executor.name(), observerName, event.getEventSequenceNumber());
                        return consumed;
                    }
                }
                currentVersion = advanceCheckpoint(consumerId, new EventCheckpoint(event.getEventSequenceNumber()), currentVersion);
                consumed++;
                recordMetric(observerName, event, start);
            }
            return consumed;
        }
    }

    public void consumeDeadEventsForObserver(String consumerId,
                                             String observerName,
                                             EventConsumer observerEventConsumer) throws Exception {
        Optional<ConsumerLock.LockHandle> held = lock.tryAcquire(consumerId);
        if (held.isEmpty()) return;
        try (var ignored = held.get()) {
            // Replay runs inline, not on the observer executor. It is operator-driven and
            // low-volume, and routing it through the shared executor would let a replay
            // storm starve live consumption.
            for (PublishedEvent event : deadEventQueue.getRetriable(consumerId)) {
                var start = Instant.now();
                try {
                    deadEventQueue.remove(consumerId, event);
                    observerEventConsumer.consume(event);
                } catch (Throwable e) {
                    try {
                        deadEventQueue.add(consumerId, event, e);
                    } catch (Throwable ignored2) {
                        logger.error("DLQ insert failed for observer dead-event {} {} — ignored",
                                observerName, event.getEventName(), ignored2);
                    }
                }
                recordMetric(observerName, event, start);
            }
        }
    }

    public int consumeEventsForSaga(String consumerId,
                                    String sagaName,
                                    String context,
                                    SagaEventConsumer sagaEventConsumer,
                                    int fetchSize) throws Throwable {
        Optional<ConsumerLock.LockHandle> held = lock.tryAcquire(consumerId);
        if (held.isEmpty()) return -1;
        try (var ignored = held.get()) {
            var cursor = readCursor(consumerId);
            long lastSeq = cursor.checkpoint == null
                    ? fetchHeadAndSeed(consumerId, SagaCheckpoint::new)
                    : cursor.checkpoint.lastSequenceNumber();
            long currentVersion = cursor.checkpoint == null
                    ? stateStore.read(consumerId).map(VersionedCheckpoint::version).orElse(cursor.version)
                    : cursor.version;
            var resp = fetchEvents(context, lastSeq, fetchSize, sagaName);

            int consumed = 0;
            for (PublishedEvent event : resp.getEvents()) {
                var start = Instant.now();
                AtomicReference<Long> sagaStateId = new AtomicReference<>();
                try {
                    SagaState newState = sagaEventConsumer.consume(buildFetcher(sagaStateId), event);
                    persistSagaResult(sagaName, sagaStateId.get(), newState);
                } catch (ConsumerDisabledException e) {
                    logger.warn("Event ignored due to consumer disabled for saga {} and event {}.",
                            sagaName, event.getEventName());
                    return consumed;
                } catch (Exception e) {
                    if (isTransient(e)) {
                        // Downed dependency / mid-burst crash: keep the checkpoint
                        // where it is so this event is redelivered when the saga's
                        // collaborator comes back, instead of stranding the saga in
                        // the DLQ (which would leave the business process undecided).
                        // Typed transient signal → engine backs off exponentially.
                        logger.warn("Transient failure for saga {} on event {} (seq {}) — "
                                        + "not advancing checkpoint, will redeliver: {}",
                                sagaName, event.getEventName(), event.getEventSequenceNumber(), rootMessage(e));
                        throw new TransientConsumerException(rootMessage(e), e);
                    }
                    deadEventQueue.add(consumerId, event, e);
                    logger.error("Event consumption error for saga {} event {} — moved to DLQ",
                            sagaName, event.getEventName(), e);
                }
                currentVersion = advanceCheckpoint(consumerId, new SagaCheckpoint(event.getEventSequenceNumber()), currentVersion);
                consumed++;
                recordMetric(sagaName, event, start);
            }
            return consumed;
        }
    }

    public void consumeDeadEventsForSaga(String consumerId,
                                         String sagaName,
                                         SagaEventConsumer sagaEventConsumer) throws Exception {
        Optional<ConsumerLock.LockHandle> held = lock.tryAcquire(consumerId);
        if (held.isEmpty()) return;
        try (var ignored = held.get()) {
            for (PublishedEvent event : deadEventQueue.getRetriable(consumerId)) {
                var start = Instant.now();
                AtomicReference<Long> sagaStateId = new AtomicReference<>();
                try {
                    deadEventQueue.remove(consumerId, event);
                    SagaState newState = sagaEventConsumer.consume(buildFetcher(sagaStateId), event);
                    persistSagaResult(sagaName, sagaStateId.get(), newState);
                } catch (ConsumerDisabledException e) {
                    logger.warn("Dead event ignored due to consumer disabled for saga {} and event {}.",
                            sagaName, event.getEventName());
                    return;
                } catch (Throwable e) {
                    deadEventQueue.add(consumerId, event, e);
                    logger.error("Dead-event reprocess failed for saga {} event {} — kept in DLQ",
                            sagaName, event.getEventName(), e);
                }
                recordMetric(sagaName, event, start);
            }
        }
    }

    // --- Async dispatch -----------------------------------------------------

    /**
     * Block until every task this consumer dispatched to an executor has finished.
     *
     * <p>Two callers depend on this:
     * <ul>
     *   <li><b>The projector head-reached gate.</b> "Consumed" means "started" for async
     *       events, so without draining first the bundle would be enabled — and start
     *       serving queries — while read-model writes were still in flight.</li>
     *   <li><b>Shutdown.</b> In-flight events are already checkpointed, so dropping them
     *       on a graceful stop would lose them silently.</li>
     * </ul>
     *
     * @return {@code true} if the consumer went idle within the deadline.
     */
    public boolean awaitConsumerQuiescence(String consumerId, Duration deadline) throws InterruptedException {
        var tracker = inFlight.get(consumerId);
        return tracker == null || tracker.await(deadline);
    }

    /** Tasks dispatched by this consumer that have not yet finished. */
    public int inFlightCount(String consumerId) {
        var tracker = inFlight.get(consumerId);
        return tracker == null ? 0 : tracker.count();
    }

    /**
     * Dispatch {@code body} and return once it has <b>started</b>, which is the point at
     * which the caller may advance the checkpoint.
     *
     * @return {@code false} if the executor had no capacity within the submit timeout, in
     *         which case the body is guaranteed never to run.
     */
    private boolean submitAsync(String consumerId, ConsumerExecutor executor, Runnable body)
            throws InterruptedException {
        var tracker = inFlight.computeIfAbsent(consumerId, k -> new InFlightTracker());
        // Enter before submitting so a drain that starts between submit and first
        // execution still observes this task.
        tracker.enter();
        Optional<CompletableFuture<Void>> started;
        try {
            started = executor.submit(() -> {
                try {
                    body.run();
                } finally {
                    tracker.exit();
                }
            }, submitTimeout);
        } catch (RuntimeException | InterruptedException e) {
            tracker.exit();
            throw e;
        }
        if (started.isEmpty()) {
            tracker.exit();
            tracker.recordSubmitTimeout();
            return false;
        }
        // Completes as soon as the task body begins running.
        started.get().join();
        return true;
    }

    /**
     * Consecutive transient failures observed in this consumer's async tasks, reset by the
     * first clean completion. Engines use it to back their fetch loop off: without that, a
     * downed dependency would be met by the loop pulling events at full speed and burning
     * the whole stream into the dead-event queue, because an async event's checkpoint has
     * already advanced and redelivery is no longer available.
     */
    public int asyncTransientFailureStreak(String consumerId) {
        var tracker = inFlight.get(consumerId);
        return tracker == null ? 0 : tracker.transientStreak();
    }

    /** Task body for an async projector event: run the handler, dead-letter on failure. */
    private void runGuarded(String consumerId, String consumerName,
                            PublishedEvent event, EventConsumer consumer) {
        var tracker = inFlight.get(consumerId);
        try {
            consumer.consume(event);
            if (tracker != null) tracker.recordSuccess();
        } catch (ConsumerDisabledException e) {
            // The consumer was paused while this task was in flight. Dropping is correct:
            // the checkpoint has advanced, and dead-lettering would surface an operator
            // action as a consumer failure.
            logger.warn("Event dropped due to consumer disabled for {} and event {}.",
                    consumerName, event.getEventName());
        } catch (Throwable e) {
            // The checkpoint has already advanced past this event, so redelivery is no
            // longer available — transient and permanent failures alike can only go to the
            // DLQ. The retry budget on @EventHandler is what absorbs transient blips; the
            // streak below is what stops the fetch loop feeding a dead dependency at full
            // speed while that budget is being exhausted event after event.
            if (tracker != null && isTransient(e)) {
                tracker.recordTransientFailure();
                logger.warn("Transient failure in async handler for {} event {} (streak {}) — "
                                + "checkpoint already advanced, dead-lettering and backing off",
                        consumerName, event.getEventName(), tracker.transientStreak());
            }
            try {
                deadEventQueue.add(consumerId, event, e);
                logger.error("Async event consumption error for {} event {} — moved to DLQ",
                        consumerName, event.getEventName(), e);
            } catch (Throwable e2) {
                logger.error("DLQ insert failed for {} event {} — will be ignored",
                        consumerName, event.getEventName(), e2);
            }
        }
    }

    private boolean submitAsync(String consumerId, String consumerName, PublishedEvent event,
                                EventConsumer consumer, ConsumerExecutor executor)
            throws InterruptedException {
        return submitAsync(consumerId, executor, () -> runGuarded(consumerId, consumerName, event, consumer));
    }

    private void awaitInlineBarrier(String consumerId, String consumerName) {
        var tracker = inFlight.get(consumerId);
        if (tracker == null || tracker.count() == 0) return;
        try {
            if (!tracker.await(inlineBarrierTimeout)) {
                logger.warn("Inline handler for {} proceeding with {} async task(s) still in flight "
                                + "after {} — it may not observe their effects",
                        consumerName, tracker.count(), inlineBarrierTimeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Per-consumer async bookkeeping: a counting latch that supports "wait until zero",
     * plus the counters the engine and the dashboard read.
     */
    private static final class InFlightTracker {
        private int count;
        private long submitTimeouts;
        private long transientFailures;
        private int transientStreak;

        synchronized void enter() {
            count++;
        }

        synchronized void exit() {
            count--;
            if (count <= 0) notifyAll();
        }

        synchronized int count() {
            return count;
        }

        synchronized void recordSubmitTimeout() {
            submitTimeouts++;
        }

        synchronized void recordTransientFailure() {
            transientFailures++;
            transientStreak++;
        }

        /**
         * A task finished cleanly, so whatever dependency was failing is answering again.
         * Only the streak resets — the cumulative totals are for the dashboard.
         */
        synchronized void recordSuccess() {
            transientStreak = 0;
        }

        synchronized long submitTimeouts() {
            return submitTimeouts;
        }

        synchronized long transientFailures() {
            return transientFailures;
        }

        synchronized int transientStreak() {
            return transientStreak;
        }

        synchronized boolean await(Duration deadline) throws InterruptedException {
            long endNanos = System.nanoTime() + deadline.toNanos();
            while (count > 0) {
                long remaining = endNanos - System.nanoTime();
                if (remaining <= 0) return false;
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
            return true;
        }
    }

    // --- Admin / status -----------------------------------------------------

    /**
     * Mirrors v1 {@code handleLastError}: if the consumer is disabled, throw
     * {@link ConsumerDisabledException}; otherwise record the error.
     */
    public void handleLastError(String consumerId, Throwable error) throws ConsumerDisabledException {
        if (!stateStore.isEnabled(consumerId)) {
            throw new ConsumerDisabledException();
        }
        stateStore.setLastError(consumerId, error);
    }

    /** Snapshot for the dashboard's {@code ConsumerFetchStatusResponseMessage} wire. */
    public ConsumerFetchStatusResponseMessage toConsumerStatus(String consumerId) {
        var resp = new ConsumerFetchStatusResponseMessage();

        long lastSeq;
        try {
            lastSeq = stateStore.read(consumerId)
                    .map(c -> c.checkpoint().lastSequenceNumber())
                    .orElseGet(this::fetchHeadOrZero);
        } catch (Exception e) {
            lastSeq = 0L;
        }
        resp.setLastEventSequenceNumber(lastSeq);

        try {
            resp.setDeadEvents(deadEventQueue.getAll(consumerId));
        } catch (Exception e) {
            resp.setDeadEvents(java.util.Collections.emptyList());
        }

        var err = stateStore.getErrorState(consumerId);
        resp.setInError(err.inError());
        resp.setErrorStartAt(toZdt(err.errorStartAt()));
        resp.setLastErrorAt(toZdt(err.lastErrorAt()));
        resp.setErrorCount(err.errorCount());
        resp.setError(err.errorMessage());
        resp.setEnabled(stateStore.isEnabled(consumerId));

        var tracker = inFlight.get(consumerId);
        if (tracker != null) {
            resp.setAsyncInFlight(tracker.count());
            resp.setAsyncSubmitTimeouts(tracker.submitTimeouts());
            resp.setAsyncTransientFailures(tracker.transientFailures());
        }
        return resp;
    }

    /**
     * v1's {@code getLastEventSequenceNumberSagaOrHead}: return the stored
     * sequence if any, otherwise fetch HEAD from the server and seed it.
     */
    public long getLastEventSequenceNumberSagaOrHead(String consumerId) throws Exception {
        var current = stateStore.read(consumerId);
        if (current.isPresent()) {
            return current.get().checkpoint().lastSequenceNumber();
        }
        long head = fetchHead();
        try {
            stateStore.commit(consumerId, new SagaCheckpoint(head), 0L);
        } catch (OptimisticLockException e) {
            // Another instance seeded it first — read again.
            return stateStore.read(consumerId).map(c -> c.checkpoint().lastSequenceNumber()).orElse(head);
        }
        return head;
    }

    // --- Internals ----------------------------------------------------------

    /**
     * Transient = the failure reflects a temporarily-unreachable collaborator
     * (broker/transport down, request timeout, DB or downstream connection
     * refused/reset), not a defect in the event or handler. Such failures must
     * NOT advance the checkpoint or dead-letter the event — they redeliver until
     * the dependency recovers (at-least-once). Permanent failures (NPE, mapping,
     * validation, …) still go to the DLQ so one poison event can't block the
     * stream forever. Detection walks the whole cause chain and matches by class
     * name + a few high-signal messages so no transport/JDBC types are imported.
     */
    private static boolean isTransient(Throwable t) {
        if (ChannelErrors.isChannelError(t)) return true;
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            var name = cur.getClass().getName();
            switch (name) {
                case "java.util.concurrent.TimeoutException",
                     "java.net.ConnectException",
                     "java.net.SocketTimeoutException",
                     "java.net.NoRouteToHostException",
                     "java.net.UnknownHostException",
                     "java.sql.SQLTransientException",
                     "java.sql.SQLRecoverableException",
                     "java.sql.SQLTransientConnectionException",
                     "java.sql.SQLNonTransientConnectionException" -> { return true; }
                default -> { /* fall through to message inspection */ }
            }
            var msg = cur.getMessage();
            if (msg != null) {
                var m = msg.toLowerCase();
                if (m.contains("connection refused") || m.contains("connection reset")
                        || m.contains("connection is closed") || m.contains("connection closed")
                        || m.contains("broken pipe") || m.contains("timed out")
                        || m.contains("temporarily unavailable") || m.contains("no available connection")) {
                    return true;
                }
            }
            if (cur.getCause() == cur) break;
        }
        return false;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }

    private record Cursor(ConsumerCheckpoint checkpoint, long version) {}

    private Cursor readCursor(String consumerId) {
        return stateStore.read(consumerId)
                .map(v -> new Cursor(v.checkpoint(), v.version()))
                .orElse(new Cursor(null, 0L));
    }

    private long advanceCheckpoint(String consumerId, ConsumerCheckpoint next, long currentVersion) {
        try {
            return stateStore.commit(consumerId, next, currentVersion);
        } catch (OptimisticLockException e) {
            // We hold the consumer lock — a version mismatch means a previous
            // run leaked checkpoint state. Re-read and continue from there.
            logger.warn("Checkpoint version drift on '{}': {}; reconciling and continuing.",
                    consumerId, e.getMessage());
            long actual = stateStore.read(consumerId).map(VersionedCheckpoint::version).orElse(0L);
            try {
                return stateStore.commit(consumerId, next, actual);
            } catch (OptimisticLockException ee) {
                throw new IllegalStateException("checkpoint commit race for " + consumerId, ee);
            }
        }
    }

    private long fetchHeadAndSeed(String consumerId,
                                  java.util.function.LongFunction<ConsumerCheckpoint> ctor) {
        try {
            long head = fetchHead();
            stateStore.commit(consumerId, ctor.apply(head), 0L);
            return head;
        } catch (OptimisticLockException e) {
            return stateStore.read(consumerId).map(c -> c.checkpoint().lastSequenceNumber()).orElse(0L);
        } catch (Exception e) {
            throw new RuntimeException("failed to seed checkpoint from server head", e);
        }
    }

    private long fetchHead() throws Exception {
        var resp = (EventLastSequenceNumberResponse) eventoServer
                .request(new EventLastSequenceNumberRequest(), timeoutMillis, TimeUnit.MILLISECONDS)
                .get();
        return resp.getNumber();
    }

    private long fetchHeadOrZero() {
        try {
            return fetchHead();
        } catch (Exception e) {
            return 0L;
        }
    }

    private EventFetchResponse fetchEvents(String context, long lastSeq, int fetchSize, String consumerName)
            throws Exception {
        return (EventFetchResponse) eventoServer
                .request(new EventFetchRequest(context, lastSeq, fetchSize, consumerName),
                        timeoutMillis, TimeUnit.MILLISECONDS)
                .get();
    }

    private SagaStateFetcher buildFetcher(AtomicReference<Long> idHolder) {
        return (name, prop, value) -> {
            var stored = sagaStateStore.findByAssociation(name, prop, value)
                    .orElse(new com.evento.common.messaging.consumer.StoredSagaState(null, null));
            idHolder.set(stored.getId());
            return stored.getState();
        };
    }

    private void persistSagaResult(String sagaName, Long sagaStateId, SagaState newState) {
        if (newState == null) return;
        if (newState.isEnded()) {
            if (sagaStateId != null) sagaStateStore.delete(sagaStateId);
            return;
        }
        if (sagaStateId == null) {
            sagaStateStore.insert(sagaName, newState);
        } else {
            sagaStateStore.update(sagaStateId, newState);
        }
    }

    private void recordMetric(String consumerName, PublishedEvent event, Instant start) {
        if (performanceService == null) return;
        performanceService.sendServiceTimeMetric(
                eventoServer.getBundleId(),
                eventoServer.getInstanceId(),
                consumerName,
                event.getEventMessage(),
                start,
                event.getEventMessage().isForceTelemetry());
    }

    private static ZonedDateTime toZdt(Instant i) {
        return i == null ? null : ZonedDateTime.ofInstant(i, ZoneId.systemDefault());
    }

    /** Exposed for tests + Spring wiring inspection. */
    public ObjectMapper getObjectMapper() { return objectMapper; }

    // --- Builder ------------------------------------------------------------

    public static final class Builder {
        private EventoServer eventoServer;
        private ConsumerLock lock;
        private ConsumerStateStore stateStore;
        private SagaStateStore sagaStateStore;
        private DeadEventQueue deadEventQueue;
        private DedupeStore dedupeStore;
        private PerformanceService performanceService;
        private ObjectMapper objectMapper;
        private ConsumerExecutor observerExecutor;
        private long timeoutMillis = 30_000L;
        private Duration submitTimeout = Duration.ofSeconds(5);
        private Duration inlineBarrierTimeout = Duration.ofSeconds(30);

        public Builder eventoServer(EventoServer eventoServer) { this.eventoServer = eventoServer; return this; }
        public Builder lock(ConsumerLock v) { this.lock = v; return this; }
        public Builder stateStore(ConsumerStateStore v) { this.stateStore = v; return this; }
        public Builder sagaStateStore(SagaStateStore v) { this.sagaStateStore = v; return this; }
        public Builder deadEventQueue(DeadEventQueue v) { this.deadEventQueue = v; return this; }
        public Builder dedupeStore(DedupeStore v) { this.dedupeStore = v; return this; }
        public Builder performanceService(PerformanceService v) { this.performanceService = v; return this; }
        public Builder objectMapper(ObjectMapper v) { this.objectMapper = v; return this; }
        /**
         * Legacy entry point: adapts a plain {@link Executor} with no capacity bound,
         * preserving the historical observer behaviour exactly. Prefer
         * {@link #observerExecutor(ConsumerExecutor)} with
         * {@link ConsumerExecutors#virtual(String, int)} — unbounded fan-out gives no
         * backpressure when an observer is catching up from a cold checkpoint.
         */
        public Builder observerExecutor(Executor v) {
            this.observerExecutor = v == null ? null : ConsumerExecutors.unbounded("observer", v);
            return this;
        }

        /** Fallback executor for observer events whose handler names none. */
        public Builder observerExecutor(ConsumerExecutor v) { this.observerExecutor = v; return this; }

        public Builder timeoutMillis(long v) { this.timeoutMillis = v; return this; }

        /**
         * How long a consume cycle waits for executor capacity before ending the cycle.
         * Keep it short: the cycle holds the {@code ConsumerLock}, and the JDBC lock
         * implementations pin a pooled connection for its whole duration.
         */
        public Builder submitTimeout(Duration v) { this.submitTimeout = v; return this; }

        /**
         * How long an inline handler waits for earlier async events on the same consumer
         * to finish before proceeding anyway (with a warning).
         */
        public Builder inlineBarrierTimeout(Duration v) { this.inlineBarrierTimeout = v; return this; }

        public ConsumerProcessor build() {
            if (eventoServer == null) throw new IllegalStateException("eventoServer required");
            if (lock == null) throw new IllegalStateException("lock required");
            if (stateStore == null) throw new IllegalStateException("stateStore required");
            if (sagaStateStore == null) throw new IllegalStateException("sagaStateStore required");
            if (deadEventQueue == null) throw new IllegalStateException("deadEventQueue required");
            if (observerExecutor == null) throw new IllegalStateException("observerExecutor required");
            return new ConsumerProcessor(this);
        }
    }
}
