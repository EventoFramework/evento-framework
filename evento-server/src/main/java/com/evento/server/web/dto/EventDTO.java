package com.evento.server.web.dto;

import com.evento.server.es.eventstore.EventStoreEntry;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Map;

@Data
public class EventDTO {
    private String eventSequenceNumber;
    private String eventName;
    private String aggregateId;
    private String context;
    /** Epoch millis. */
    private Long createdAt;
    /** Epoch millis, {@code null} unless the aggregate was deleted. */
    private Long deletedAt;
    private Map<String, String> metadata;
    private JsonNode event;

    public EventDTO() {
    }
    public EventDTO(EventStoreEntry entry) {
        eventSequenceNumber = entry.getEventSequenceNumber().toString();
        eventName = entry.getEventName();
        aggregateId = entry.getAggregateId();
        context =  entry.getContext();
        createdAt = entry.createdAtInstant() == null ? null : entry.createdAtInstant().toEpochMilli();
        deletedAt = entry.deletedAtInstant() == null ? null : entry.deletedAtInstant().toEpochMilli();
        metadata = entry.getEventMessage().getMetadata();
        event = entry.getEventMessage().getSerializedPayload().getTree();
    }

}
