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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    private final Executor observerExecutor;
    private final long timeoutMillis;

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
                    observerExecutor.execute(() -> {
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
            for (PublishedEvent event : deadEventQueue.getRetriable(consumerId)) {
                var start = Instant.now();
                observerExecutor.execute(() -> {
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
                });
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
        private Executor observerExecutor;
        private long timeoutMillis = 30_000L;

        public Builder eventoServer(EventoServer eventoServer) { this.eventoServer = eventoServer; return this; }
        public Builder lock(ConsumerLock v) { this.lock = v; return this; }
        public Builder stateStore(ConsumerStateStore v) { this.stateStore = v; return this; }
        public Builder sagaStateStore(SagaStateStore v) { this.sagaStateStore = v; return this; }
        public Builder deadEventQueue(DeadEventQueue v) { this.deadEventQueue = v; return this; }
        public Builder dedupeStore(DedupeStore v) { this.dedupeStore = v; return this; }
        public Builder performanceService(PerformanceService v) { this.performanceService = v; return this; }
        public Builder objectMapper(ObjectMapper v) { this.objectMapper = v; return this; }
        public Builder observerExecutor(Executor v) { this.observerExecutor = v; return this; }
        public Builder timeoutMillis(long v) { this.timeoutMillis = v; return this; }

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
