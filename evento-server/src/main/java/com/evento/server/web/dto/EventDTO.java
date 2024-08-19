package com.evento.server.web.dto;

import com.evento.server.es.eventstore.EventStoreEntry;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.sql.Timestamp;
import java.util.Map;

@Data
public class EventDTO {
    private String eventSequenceNumber;
    private String eventName;
    private String aggregateId;
    private String context;
    private Timestamp  createdAt;
    private Timestamp deletedAt;
    private Map<String, String> metadata;
    private JsonNode event;

    public EventDTO() {
    }
    public EventDTO(EventStoreEntry entry) {
        eventSequenceNumber = entry.getEventSequenceNumber().toString();
        eventName = entry.getEventName();
        aggregateId = entry.getAggregateId();
        context =  entry.getContext();
        createdAt = entry.getCreatedAt();
        deletedAt = entry.getDeletedAt();
        metadata = entry.getEventMessage().getMetadata();
        event = entry.getEventMessage().getSerializedPayload().getTree();
    }

}
