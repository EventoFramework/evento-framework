package com.evento.transport.reconnect;

import java.time.Duration;

/**
 * Decides how long to wait before the next reconnect attempt and when to give up.
 * Implementations must be thread-safe.
 */
public interface ReconnectStrategy {

    /**
     * Delay before the {@code attempt}-th reconnect attempt (1-based: the first reconnect
     * after a successful connection drop is attempt 1).
     */
    Duration nextDelay(int attempt);

    /**
     * Whether the caller should give up after {@code attempt} consecutive failures.
     * Implementations may return false for unbounded retry.
     */
    boolean shouldGiveUp(int attempt);
}
