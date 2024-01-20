package org.evento.server.es;

import org.evento.common.modeling.messaging.message.application.DomainEventMessage;
import org.evento.common.modeling.state.SerializedAggregateState;

import java.util.List;

/**
 * The AggregateStory class represents the story of an aggregate, consisting of its state and a list of domain events.
 */
public record AggregateStory(SerializedAggregateState<?> state, List<DomainEventMessage> events) {

}
