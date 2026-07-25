package com.evento.common.messaging.consumer;

/**
 * Point-in-time counters for a {@link ConsumerExecutor}.
 *
 * <p>{@link #rejected()} is the one to alert on: it counts admissions that timed out, which
 * is the executor telling you it is the bottleneck. A steadily climbing value means
 * consumers are spending their cycles waiting for capacity and repeatedly re-fetching the
 * same events — raise the capacity, or find out why handlers are slow.
 *
 * @param name      executor name
 * @param capacity  permit count, or {@link Integer#MAX_VALUE} if unbounded
 * @param inFlight  admitted and not yet finished
 * @param admitted  cumulative tasks granted a permit
 * @param rejected  cumulative submissions refused (no capacity within the timeout, or shut down)
 * @param completed cumulative tasks that finished, successfully or not
 * @param failed    cumulative tasks whose body threw past the consumer's own wrapper —
 *                  normally zero, since the consume loop catches everything itself
 */
public record ConsumerExecutorStats(
        String name,
        int capacity,
        int inFlight,
        long admitted,
        long rejected,
        long completed,
        long failed
) {
}
