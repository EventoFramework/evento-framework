package com.evento.common.messaging.consumer.v2;

import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;

/** Test-only helpers for building {@link PublishedEvent} fixtures. */
final class TestEvents {

    private TestEvents() {}

    static PublishedEvent event(long seq, String name) {
        return event(seq, name, "aggregate-" + seq, "ctx");
    }

    static PublishedEvent event(long seq, String name, String aggregateId, String context) {
        var msg = new DomainEventMessage();
        msg.setContext(context);
        var pe = new PublishedEvent();
        pe.setEventSequenceNumber(seq);
        pe.setEventName(name);
        pe.setAggregateId(aggregateId);
        pe.setEventMessage(msg);
        pe.setCreatedAt(System.currentTimeMillis());
        return pe;
    }
}
