package com.evento.common.messaging.consumer;

import com.evento.common.messaging.consumer.StoredSagaState;
import com.evento.common.modeling.state.SagaState;

import java.util.Collection;
import java.util.Optional;

/**
 * Saga instance storage. Independent from {@link ConsumerStateStore}: a saga's
 * checkpoint lives in the state store (as a {@link SagaCheckpoint}); the
 * individual saga <em>instances</em>, keyed by association property/value, live
 * here.
 *
 * <p>The same {@link StoredSagaState} value type used by v1 is reused so the
 * v2 engine can hand the saga handler the exact shape it already expects.
 */
public interface SagaStateStore {

    /**
     * Look up a saga instance by an association entry. Returns empty if no
     * instance currently has that {@code (property, value)} pair in its
     * associations map.
     */
    Optional<StoredSagaState> findByAssociation(String sagaName,
                                                String associationProperty,
                                                String associationValue);

    /** Every persisted instance of the named saga. Used by the dashboard. */
    Collection<StoredSagaState> findAll(String sagaName);

    /** Insert a brand-new instance and return the assigned id. */
    long insert(String sagaName, SagaState state);

    /** Overwrite an existing instance's state. */
    void update(long id, SagaState state);

    /** Drop an instance — typically when the saga reaches an end state. */
    void delete(long id);
}
