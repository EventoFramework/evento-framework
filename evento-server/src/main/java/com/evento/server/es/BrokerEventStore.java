package com.evento.server.es;

import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.state.SerializedAggregateState;

import java.util.Optional;

/**
 * Minimal event-store contract needed by {@link CommandBrokerHandler}.
 * The production implementation is {@link EventStore}; tests supply
 * {@code CommandAwareTestEventStore} (or equivalent) backed by an in-memory list.
 *
 * <p>The idempotency-key methods (G5) are defaults, so a store written before them keeps
 * compiling and simply stores no keys: every keyed command is then executed as an unkeyed one.
 */
public interface BrokerEventStore {

    AggregateStory fetchAggregateStory(String aggregateId,
                                       boolean invalidateCaches,
                                       boolean invalidateSnapshot);

    long publishEvent(EventMessage<?> eventMessage, String aggregateId);

    void saveSnapshot(String aggregateId, Long seqNum, SerializedAggregateState<?> state);

    void deleteAggregate(String aggregateId);

    /**
     * The event stored by the command that carried {@code idempotencyKey}, if any.
     *
     * @param idempotencyKey a non-null key
     * @return the stored event, empty when no command with that key has been stored
     */
    default Optional<StoredEvent> findByIdempotencyKey(String idempotencyKey) {
        return Optional.empty();
    }

    /**
     * Appends {@code eventMessage} recording {@code idempotencyKey} with it, atomically with the
     * append: two appends with one key never both succeed, whichever servers run them.
     *
     * @param idempotencyKey the command's key, {@code null} for an unkeyed append
     * @return the event's sequence number
     * @throws IdempotencyKeyConflictException when an event with that key is already stored;
     *                                         nothing was appended
     */
    default long publishEvent(EventMessage<?> eventMessage, String aggregateId, String idempotencyKey) {
        return publishEvent(eventMessage, aggregateId);
    }

    /** An event as stored, with the sequence number it was given. */
    record StoredEvent(long eventSequenceNumber, EventMessage<?> eventMessage) {
    }

    /** The key of an append is already taken by a stored event (see {@link #publishEvent(EventMessage, String, String)}). */
    class IdempotencyKeyConflictException extends RuntimeException {
        public IdempotencyKeyConflictException(String idempotencyKey) {
            super("an event with idempotency key '" + idempotencyKey + "' is already stored");
        }
    }
}
