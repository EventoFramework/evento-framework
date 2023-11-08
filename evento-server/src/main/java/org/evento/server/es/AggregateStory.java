package org.evento.server.es;

import org.evento.common.modeling.messaging.message.application.DomainEventMessage;
import org.evento.common.modeling.state.SerializedAggregateState;

import java.util.List;

public class AggregateStory {

    private final SerializedAggregateState<?> state;
    private final List<DomainEventMessage> events;

    public AggregateStory(SerializedAggregateState<?> state, List<DomainEventMessage> events) {
        this.state = state;
        this.events = events;
    }

    public SerializedAggregateState<?> getState() {
        return state;
    }

    public List<DomainEventMessage> getEvents() {
        return events;
    }
}
