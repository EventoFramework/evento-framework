package org.evento.server.es;

import org.evento.common.modeling.messaging.message.application.DomainEventMessage;
import org.evento.common.modeling.state.SerializedAggregateState;

import java.util.List;

public record AggregateStory(SerializedAggregateState<?> state, List<DomainEventMessage> events) {

}
