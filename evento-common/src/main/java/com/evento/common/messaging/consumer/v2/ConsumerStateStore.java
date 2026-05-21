package com.evento.common.messaging.consumer.v2;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Persistent home for every consumer's {@link ConsumerCheckpoint}, plus the
 * admin bookkeeping the dashboard reads:
 *
 * <ul>
 *   <li><b>Checkpoint</b> — read / commit (with optimistic versioning) / delete /
 *       listConsumers.</li>
 *   <li><b>Enabled flag</b> — defaults to {@code true}; the dashboard toggles
 *       this to pause a consumer without redeploying.</li>
 *   <li><b>Error history</b> — last error message + first/last seen + count;
 *       cleared on every successful checkpoint commit.</li>
 * </ul>
 *
 * <p>The cross-instance exclusive lock lives in {@link ConsumerLock} (not here)
 * so the two responsibilities can be backed by independent technologies if a
 * deployment wants to (e.g. Redis lock + Postgres state).
 */
public interface ConsumerStateStore extends AutoCloseable {

    // --- Checkpoint ----------------------------------------------------------

    /**
     * @return the current checkpoint plus its version, or {@link Optional#empty()}
     *         if this consumer has never committed.
     */
    Optional<VersionedCheckpoint> read(String consumerId);

    /**
     * Atomically advance the consumer to {@code checkpoint}, but only if its
     * persisted version still equals {@code expectedVersion}. For a first-time
     * commit pass {@code expectedVersion = 0}.
     *
     * <p>A successful commit clears any previously recorded error.
     *
     * @return the new version (always {@code expectedVersion + 1}).
     * @throws OptimisticLockException when another writer raced ahead.
     */
    long commit(String consumerId, ConsumerCheckpoint checkpoint, long expectedVersion)
            throws OptimisticLockException;

    /** Forget everything about a consumer. Idempotent. */
    void delete(String consumerId);

    /**
     * Stream every known consumer id. Order is unspecified; callers must close
     * the stream since JDBC-backed impls may own a cursor.
     */
    Stream<String> listConsumers();

    // --- Enable / disable ---------------------------------------------------

    /** Defaults to {@code true} for consumers that have never been toggled. */
    boolean isEnabled(String consumerId);

    void setEnabled(String consumerId, boolean enabled);

    // --- Error tracking -----------------------------------------------------

    /**
     * Record that consumption failed for this consumer. First call after a
     * clean run pins {@link ConsumerErrorState#errorStartAt}; subsequent calls
     * bump {@link ConsumerErrorState#errorCount} and refresh
     * {@link ConsumerErrorState#lastErrorAt} but keep the start pinned.
     */
    void setLastError(String consumerId, Throwable error);

    /**
     * @return the current error state, or {@link ConsumerErrorState#healthy()}
     *         if the consumer never failed (or recovered via a successful commit).
     */
    ConsumerErrorState getErrorState(String consumerId);

    @Override
    default void close() {
        // Default: nothing to release. JDBC impls override.
    }
}
