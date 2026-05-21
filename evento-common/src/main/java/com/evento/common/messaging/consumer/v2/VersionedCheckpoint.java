package com.evento.common.messaging.consumer.v2;

/**
 * Pair of a {@link ConsumerCheckpoint} plus the optimistic-lock version that
 * the store recorded alongside it. Callers must echo {@link #version()} back to
 * {@link ConsumerStateStore#commit} when they persist the next checkpoint —
 * mismatch surfaces as {@link OptimisticLockException}.
 *
 * <p>For a brand-new consumer (no row yet), readers see
 * {@link java.util.Optional#empty()}; first {@code commit} must pass
 * {@code expectedVersion == 0}.
 */
public record VersionedCheckpoint(ConsumerCheckpoint checkpoint, long version) {
}
