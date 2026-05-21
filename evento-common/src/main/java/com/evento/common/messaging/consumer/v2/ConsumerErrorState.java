package com.evento.common.messaging.consumer.v2;

import java.time.Instant;

/**
 * Snapshot of a consumer's error history. {@link #inError} flips true on the
 * first {@link ConsumerStateStore#setLastError} after a clean run; subsequent
 * failures bump {@link #errorCount} and refresh {@link #lastErrorAt} but keep
 * {@link #errorStartAt} pinned to when the streak began.
 *
 * <p>An ok consumer (no errors ever, or recovered after a successful commit)
 * is represented by {@link #healthy()}.
 */
public record ConsumerErrorState(
        boolean inError,
        Instant errorStartAt,
        Instant lastErrorAt,
        long errorCount,
        String errorMessage
) {
    public static ConsumerErrorState healthy() {
        return new ConsumerErrorState(false, null, null, 0L, null);
    }
}
