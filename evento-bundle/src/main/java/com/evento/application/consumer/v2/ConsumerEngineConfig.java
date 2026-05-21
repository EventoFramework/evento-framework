package com.evento.application.consumer.v2;

import com.evento.common.messaging.consumer.v2.ConsumerProcessor;
import com.evento.common.messaging.consumer.v2.ConsumerStateStore;
import com.evento.common.messaging.consumer.v2.DeadEventQueue;

/**
 * Bundle of v2 SPIs the engines need, returned by the
 * {@code EventoBundle.Builder.consumerEngineConfigBuilder} lambda when the
 * v2 consumer path is opt-in.
 *
 * <p>Three pieces are pulled out (rather than just the {@link ConsumerProcessor})
 * because the engines also need:
 * <ul>
 *   <li>{@link ConsumerStateStore} — for the {@code isEnabled(consumerId)} probe
 *       at the top of each loop iteration (mirrors v1 behaviour).</li>
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
    public ConsumerEngineConfig {
        if (processor == null) throw new IllegalArgumentException("processor is required");
        if (stateStore == null) throw new IllegalArgumentException("stateStore is required");
        if (deadEventQueue == null) throw new IllegalArgumentException("deadEventQueue is required");
    }
}
