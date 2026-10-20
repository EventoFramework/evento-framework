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
 *       lock, so a waiting or executing duplicate pins no connection. It is marked completed
 *       once the event is stored (in the append statement) or the handler produced no event,
 *       and kept; it is deleted when the execution failed.</li>
 * </ul>
 *
 * <h2>The aggregate fence (C1)</h2>
 * Each event of an aggregate command carries the aggregate's version ({@code es__events.aggregate_seq}:
 * 1, 2, 3...) under a unique index on {@code (aggregate_id, aggregate_seq)}. The story is read
 * with the version it ends at ({@link KeyedAggregateStory#aggregateVersion()}) and the event is
 * appended as the next one ({@link #appendAggregateEvent}): a second append from the same story
 * (a server that lost the aggregate lock, a command with no lock at all) takes a version that is
 * already taken, and the database refuses it ({@link AggregateFenceException}). The lock stays
 * the way commands wait for each other; the fence is what makes a lost or missing lock unable to
 * fork the aggregate. A store written before the fence keeps compiling: the defaults report no
 * version and append unfenced.
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
     * Deletes the (uncompleted) claim of {@code owner} after a failed execution, which a retry
     * may then run again.
     */
    default void releaseIdempotencyClaim(String idempotencyKey, String owner) {
    }

    /**
     * The aggregate story and, when {@code idempotencyKey} is not null, what is recorded for the
     * key. A store that can should read both in one round trip ({@link EventStore} does: the key
     * lookup is a branch of the events query), so that a keyed aggregate command costs no round
     * trip more than an unkeyed one. This default does a lookup and then the fetch.
     *
     * @return the story (never null) and the record ({@code null} when nothing is recorded or
     * {@code idempotencyKey} is null)
     */
    default KeyedAggregateStory fetchAggregateStory(String aggregateId, boolean invalidateCaches,
                                                    boolean invalidateSnapshot, String idempotencyKey) {
        IdempotencyRecord record = idempotencyKey == null ? null : findIdempotencyRecord(idempotencyKey).orElse(null);
        return new KeyedAggregateStory(fetchAggregateStory(aggregateId, invalidateCaches, invalidateSnapshot), record);
    }

    /**
     * Claims {@code idempotencyKey} unless something settles it, and reports what is recorded
     * when it does not claim. {@link EventStore} does it in one statement, which needs no lookup
     * before and no re-check after; this default composes the older calls (lookup, claim,
     * re-check under the claim), so a store written before keeps its guarantees.
     *
     * @return {@code claimed} true when {@code owner} now holds the claim; otherwise the record
     * as read (may be {@code null}: nothing visible yet, e.g. a claim inserted concurrently)
     */
    default ClaimAttempt claimOrFindIdempotencyKey(String idempotencyKey, String commandType, String aggregateId,
                                                   String owner, long leaseMs) {
        var before = findIdempotencyRecord(idempotencyKey).orElse(null);
        if (before != null && (before.event() != null || before.claim() == ClaimState.RUNNING
                || before.claim() == ClaimState.COMPLETED || !before.belongsTo(commandType, aggregateId))) {
            return new ClaimAttempt(false, before);
        }
        if (!claimIdempotencyKey(idempotencyKey, commandType, aggregateId, owner, leaseMs)) {
            return new ClaimAttempt(false, null);
        }
        var after = findIdempotencyRecord(idempotencyKey).orElse(null);
        if (after != null && after.event() != null) {
            releaseIdempotencyClaim(idempotencyKey, owner);
            return new ClaimAttempt(false, after);
        }
        return new ClaimAttempt(true, null);
    }

    /**
     * The keyed append of a service command whose key {@code claimOwner} claimed: the event,
     * and the claim settled with it. {@link EventStore} marks the claim completed in the append
     * statement itself (same transaction, no extra round trip) and keeps the row, which is what
     * lets {@link #claimOrFindIdempotencyKey} skip the re-check. This default appends and then
     * deletes the claim, as before.
     *
     * @throws IdempotencyKeyConflictException when an event with that key is already stored
     */
    default long publishEvent(EventMessage<?> eventMessage, String aggregateId, String idempotencyKey,
                              String commandType, String claimOwner) {
        long seq = publishEvent(eventMessage, aggregateId, idempotencyKey, commandType);
        if (claimOwner != null) releaseIdempotencyClaim(idempotencyKey, claimOwner);
        return seq;
    }

    /**
     * Appends the event of an aggregate command as version {@code expectedVersion + 1} of the
     * aggregate, atomically with the check that the version is free (see the class comment),
     * together with the key as in {@link #publishEvent(EventMessage, String, String, String)}.
     *
     * @param expectedVersion the version the story ended at
     *                        ({@link KeyedAggregateStory#aggregateVersion()}), {@code null} to
     *                        append unfenced
     * @return the event's sequence number
     * @throws AggregateFenceException         when the aggregate moved past {@code expectedVersion};
     *                                         nothing was appended
     * @throws IdempotencyKeyConflictException when an event with that key is already stored
     */
    default long appendAggregateEvent(EventMessage<?> eventMessage, String aggregateId, String idempotencyKey,
                                      String commandType, Long expectedVersion) {
        return publishEvent(eventMessage, aggregateId, idempotencyKey, commandType);
    }

    /**
     * Appends the events of one aggregate command (F9) as {@link #appendAggregateEvent} appends
     * one, all of them or none: versions {@code expectedVersion + 1} onwards, consecutive in the
     * store, the key on the last one.
     *
     * @return the sequence number of the last event
     * @throws UnsupportedOperationException when this store cannot append several events atomically
     */
    default long appendAggregateEvents(java.util.List<? extends EventMessage<?>> events, String aggregateId,
                                       String idempotencyKey, String commandType, Long expectedVersion) {
        if (events.size() == 1) {
            return appendAggregateEvent(events.getFirst(), aggregateId, idempotencyKey, commandType, expectedVersion);
        }
        throw new UnsupportedOperationException(getClass().getSimpleName()
                + " cannot append several events of one command atomically");
    }

    /**
     * Whether aggregate appends are fenced now, so that an aggregate command may run without its
     * lock (optimistic concurrency): two commands from one story cannot both append, the loser
     * is told so ({@link AggregateFenceException}) and the broker runs it again on the new story.
     * A store that cannot fence answers {@code false}, and every aggregate command takes its lock.
     */
    default boolean fencesAggregates() {
        return false;
    }

    /**
     * A story with the record of the command's key, read together.
     *
     * @param aggregateVersion the aggregate's version the story ends at (0 for an aggregate with
     *                         no fenced event yet), read in the same snapshot as the events;
     *                         {@code null} when the store cannot fence appends
     */
    record KeyedAggregateStory(AggregateStory story, IdempotencyRecord record, Long aggregateVersion) {
        public KeyedAggregateStory(AggregateStory story, IdempotencyRecord record) {
            this(story, record, null);
        }
    }

    /** The outcome of {@link #claimOrFindIdempotencyKey}. */
    record ClaimAttempt(boolean claimed, IdempotencyRecord record) {
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

    /**
     * The aggregate moved past the version its story ended at: another append from an older
     * or concurrent story got there first. Nothing was appended; the command can be sent again
     * and will run on the new story.
     */
    class AggregateFenceException extends RuntimeException {
        public AggregateFenceException(String aggregateId, long expectedVersion) {
            super("aggregate '" + aggregateId + "' changed while the command ran: it is no longer at version "
                    + expectedVersion + "; nothing was stored, send the command again");
        }
    }

    /** The key of an append is already taken by a stored event (see {@link #publishEvent(EventMessage, String, String, String)}). */
    class IdempotencyKeyConflictException extends RuntimeException {
        public IdempotencyKeyConflictException(String idempotencyKey) {
            super("an event with idempotency key '" + idempotencyKey + "' is already stored");
        }
    }
}
