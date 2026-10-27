package com.evento.test;

import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.messaging.message.application.ServiceEventMessage;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.messaging.payload.Event;
import com.evento.common.modeling.messaging.payload.ServiceEvent;

/** Events as a consumer receives them from the event store. */
final class Events {

    private Events() {
    }

    static PublishedEvent published(Event event, long sequence) {
        EventMessage<?> message = switch (event) {
            case DomainEvent e -> new DomainEventMessage(e);
            case ServiceEvent e -> new ServiceEventMessage(e);
            default -> throw new IllegalArgumentException("not a DomainEvent or ServiceEvent: " + event.getClass().getName());
        };
        var published = new PublishedEvent();
        published.setEventMessage(message);
        published.setEventName(message.getEventName());
        published.setEventSequenceNumber(sequence);
        published.setCreatedAt(message.getTimestamp());
        return published;
    }
}
