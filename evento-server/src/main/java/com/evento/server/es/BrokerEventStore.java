package com.evento.server.es;

import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.state.SerializedAggregateState;

import java.util.Objects;
import java.util.Optional;

/**
 * Minimal event-store contract needed by {@link CommandBrokerHandler}.
 * The production implementation is {@link EventStore}; tests supply
 * {@code CommandAwareTestEventStore} (or equivalent) backed by an in-memory list.
 *
 * <p>The idempotency-key methods (G5) are defaults, so a store written before them keeps
 * compiling and simply stores no keys: {@link #idempotencySupported()} answers {@code false},
 * and the broker then executes every keyed command as an unkeyed one, without looking anything
 * up or taking any claim.
 *
 * <h2>What is recorded for a key</h2>
 * <ul>
 *   <li>The <b>event</b> of the command, in {@code es__events} with the key and the command's
 *       identity (payload type; the aggregate id is the event's), appended atomically under a
 *       partial unique index: no two appends of one key ever both succeed.</li>
 *   <li>A <b>claim</b> in {@code es__idempotency_claims} while a service command with the key
 *       executes: owner, identity, a lease on the database clock. It is a row, not a session
 *       lock, so a waiting or executing duplicate pins no connection. It is deleted once the
 *       event is stored, kept (completed) when the handler produced no event, and deleted when
 *       the execution failed.</li>
 * </ul>
 */
public interface BrokerEventStore {

    AggregateStory fetchAggregateStory(String aggregateId,
                                       boolean invalidateCaches,
                                       boolean invalidateSnapshot);

    long publishEvent(EventMessage<?> eventMessage, String aggregateId);

    void saveSnapshot(String aggregateId, Long seqNum, SerializedAggregateState<?> state);

    void deleteAggregate(String aggregateId);

    /**
     * Whether this store records idempotency keys. When it does not (a store written before
     * G5, a schema without the index or the claims table, a database other than PostgreSQL),
     * the broker runs keyed commands exactly like unkeyed ones.
     */
    default boolean idempotencySupported() {
        return false;
    }

    /**
     * Everything recorded for {@code idempotencyKey}, read in one statement (one snapshot):
     * the stored event, if any, and the claim, if any.
     *
     * @param idempotencyKey a non-null key
     * @return empty when nothing is recorded for the key
     */
    default Optional<IdempotencyRecord> findIdempotencyRecord(String idempotencyKey) {
        return Optional.empty();
    }

    /**
     * Appends {@code eventMessage} recording {@code idempotencyKey} and the command's type with
     * it, atomically with the append: two appends with one key never both succeed, whichever
     * servers run them. Falls back to the unkeyed append when keys are not supported.
     *
     * @param idempotencyKey the command's key, {@code null} for an unkeyed append
     * @param commandType    the command's payload type, recorded as part of its identity
     * @return the event's sequence number
     * @throws IdempotencyKeyConflictException when an event with that key is already stored;
     *                                         nothing was appended
     */
    default long publishEvent(EventMessage<?> eventMessage, String aggregateId, String idempotencyKey,
                              String commandType) {
        return publishEvent(eventMessage, aggregateId);
    }

    /**
     * Claims {@code idempotencyKey} for one execution: inserts the claim, or takes over a claim
     * whose lease expired (its owner died) as long as it is not completed and names the same
     * command. Atomic: of any number of concurrent claimers, on any servers, at most one wins.
     *
     * @param leaseMs how long the claim stands without being renewed (database clock)
     * @return {@code true} when {@code owner} now holds the claim
     */
    default boolean claimIdempotencyKey(String idempotencyKey, String commandType, String aggregateId,
                                        String owner, long leaseMs) {
        return true;
    }

    /**
     * Extends the lease of a claim {@code owner} holds.
     *
     * @return {@code false} when {@code owner} no longer holds it (it expired and was taken over)
     */
    default boolean renewIdempotencyClaim(String idempotencyKey, String owner, long leaseMs) {
        return true;
    }

    /**
     * Marks the claim of {@code owner} completed without an event (a handler that returned
     * nothing): later duplicates are answered with an empty result instead of running again.
     *
     * @return {@code false} when {@code owner} no longer holds the claim
     */
    default boolean completeIdempotencyClaim(String idempotencyKey, String owner) {
        return true;
    }

    /**
     * Deletes the (uncompleted) claim of {@code owner}: after the event carrying the key is
     * stored, or after a failed execution, which a retry may then run again.
     */
    default void releaseIdempotencyClaim(String idempotencyKey, String owner) {
    }

    /** An event as stored, with the sequence number it was given. */
    record StoredEvent(long eventSequenceNumber, EventMessage<?> eventMessage) {
    }

    /** The state of the claim of a key, as seen by {@link #findIdempotencyRecord}. */
    enum ClaimState {
        /** No claim row. */
        NONE,
        /** An execution holds the key and its lease stands. */
        RUNNING,
        /** An execution held the key but its lease ran out: it may be taken over. */
        EXPIRED,
        /** An execution completed without producing an event. */
        COMPLETED
    }

    /**
     * What is recorded for one key. {@code commandType} and {@code aggregateId} are the identity
     * of the command that owns the key, from the stored event when there is one (its
     * {@code commandType} is {@code null} for an event stored before identities were recorded),
     * else from the claim.
     *
     * @param event the stored event, {@code null} when none
     */
    record IdempotencyRecord(String commandType, String aggregateId, StoredEvent event, ClaimState claim) {

        /**
         * Whether the key belongs to the command {@code (commandType, aggregateId)}. A recorded
         * type of {@code null} (a legacy row) only compares the aggregate.
         */
        public boolean belongsTo(String otherCommandType, String otherAggregateId) {
            return (commandType == null || commandType.equals(otherCommandType))
                    && Objects.equals(aggregateId, otherAggregateId);
        }
    }

    /** The key of an append is already taken by a stored event (see {@link #publishEvent(EventMessage, String, String, String)}). */
    class IdempotencyKeyConflictException extends RuntimeException {
        public IdempotencyKeyConflictException(String idempotencyKey) {
            super("an event with idempotency key '" + idempotencyKey + "' is already stored");
        }
    }
}
