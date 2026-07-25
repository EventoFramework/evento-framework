package com.evento.application.consumer;

import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.consumer.ConsumerExecutors;
import com.evento.common.messaging.consumer.ConsumerProcessor;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.messaging.consumer.DeadEventQueue;
import com.evento.common.messaging.consumer.impl.InMemoryConsumerLock;
import com.evento.common.messaging.consumer.impl.InMemoryConsumerStateStore;
import com.evento.common.messaging.consumer.impl.InMemoryDeadEventQueue;
import com.evento.common.messaging.consumer.impl.InMemoryDedupeStore;
import com.evento.common.messaging.consumer.impl.InMemorySagaStateStore;
import com.evento.common.performance.PerformanceService;

/**
 * Bundle of v2 SPIs the engines need, returned by the
 * {@code EventoBundle.Builder.consumerEngineConfigBuilder} lambda — now the
 * primary (and only) consumer path since v2.0.
 *
 * <p>Three pieces are pulled out (rather than just the {@link ConsumerProcessor})
 * because the engines also need:
 * <ul>
 *   <li>{@link ConsumerStateStore} — for the {@code isEnabled(consumerId)} probe
 *       at the top of each loop iteration.</li>
 *   <li>{@link DeadEventQueue} — for {@code ConsumerHandle} admin operations
 *       (getAll / setRetry / remove) that the processor does not surface.</li>
 * </ul>
 *
 * <p>Callers build all three together so the processor and the engines share
 * the same persistence backing — composing a fresh DLQ instance separately
 * from the one the processor uses would be a footgun.
 */
public record ConsumerEngineConfig(
        ConsumerProcessor processor,
        ConsumerStateStore stateStore,
        DeadEventQueue deadEventQueue
) {
    /**
     * Default concurrency for the fallback observer executor. Observers are
     * at-least-once by contract and have always run asynchronously, so a modest
     * bound preserves their throughput while giving the consume loop backpressure.
     */
    public static final int DEFAULT_OBSERVER_CONCURRENCY = 32;

    public ConsumerEngineConfig {
        if (processor == null) throw new IllegalArgumentException("processor is required");
        if (stateStore == null) throw new IllegalArgumentException("stateStore is required");
        if (deadEventQueue == null) throw new IllegalArgumentException("deadEventQueue is required");
    }

    /** In-memory default wiring for demos and tests. */
    public static ConsumerEngineConfig inMemory(EventoServer eventoServer, PerformanceService performanceService) {
        var lock = new InMemoryConsumerLock();
        var stateStore = new InMemoryConsumerStateStore();
        var sagaStateStore = new InMemorySagaStateStore();
        var deadEventQueue = new InMemoryDeadEventQueue();
        var dedupeStore = new InMemoryDedupeStore();
        var processor = ConsumerProcessor.builder()
                .eventoServer(eventoServer)
                .lock(lock)
                .stateStore(stateStore)
                .sagaStateStore(sagaStateStore)
                .deadEventQueue(deadEventQueue)
                .dedupeStore(dedupeStore)
                .performanceService(performanceService)
                // Bounded, unlike the historical unbounded virtual-thread executor: an
                // observer catching up from a cold checkpoint would otherwise submit the
                // whole backlog as fast as it could fetch it.
                .observerExecutor(ConsumerExecutors.virtual("observer", DEFAULT_OBSERVER_CONCURRENCY))
                .build();
        return new ConsumerEngineConfig(processor, stateStore, deadEventQueue);
    }
}
