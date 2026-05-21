package com.evento.common.messaging.consumer.v2;

/**
 * Sealed sum type for every flavor of consumer position the framework tracks.
 *
 * <p>The position itself is always the same shape — a monotonic event sequence
 * number — but the sealed split lets the store and the engines pattern-match on
 * <em>kind</em> without carrying a separate enum, and leaves room to attach
 * kind-specific fields later (e.g. saga GC watermarks) without breaking the
 * dispatch sites.
 */
public sealed interface ConsumerCheckpoint
        permits EventCheckpoint, SagaCheckpoint, ProjectorCheckpoint {

    /**
     * Last event sequence number this consumer has acknowledged. The next event
     * the engine processes must have {@code sequenceNumber > lastSequenceNumber}.
     */
    long lastSequenceNumber();
}
