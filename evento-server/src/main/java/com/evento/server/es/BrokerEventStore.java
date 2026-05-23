package com.evento.server.es;

import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.state.SerializedAggregateState;

/**
 * Minimal event-store contract needed by {@link CommandBrokerHandler}.
 * The production implementation is {@link EventStore}; tests supply
 * {@code CommandAwareTestEventStore} (or equivalent) backed by an in-memory list.
 */
public interface BrokerEventStore {

    AggregateStory fetchAggregateStory(String aggregateId,
                                       boolean invalidateCaches,
                                       boolean invalidateSnapshot);

    long publishEvent(EventMessage<?> eventMessage, String aggregateId);

    void saveSnapshot(String aggregateId, Long seqNum, SerializedAggregateState<?> state);

    void deleteAggregate(String aggregateId);
}
