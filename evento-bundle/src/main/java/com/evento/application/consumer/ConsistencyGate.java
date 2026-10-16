package com.evento.application.consumer;

import com.evento.common.modeling.messaging.query.ConsistencyToken;

/**
 * Holds a query until the read model it reads reflects some writes (read-your-writes).
 * The answering bundle calls it before running a query that carries a
 * {@link ConsistencyToken}.
 */
@FunctionalInterface
public interface ConsistencyGate {

    /** A gate that never waits: no projector here, nothing to wait for. */
    ConsistencyGate NONE = (token, deadlineNanos) -> 0L;

    /**
     * Returns once the writes of {@code token} are reflected.
     *
     * @param deadlineNanos {@link System#nanoTime()} past which it gives up
     * @return how long it waited, in nanoseconds
     * @throws com.evento.common.modeling.exceptions.ConsistencyTimeoutException when the
     *         deadline passes first
     */
    long await(ConsistencyToken token, long deadlineNanos) throws InterruptedException;
}
