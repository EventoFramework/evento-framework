package com.evento.server.web.dto;

import com.evento.server.es.snapshot.Snapshot;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.Instant;

@Data
public class SnapshotDTO {

    private String eventSequenceNumber;
    private String aggregateId;
    private Instant updatedAt;
    private Instant deletedAt;
    private JsonNode aggregateState;

    public SnapshotDTO(Snapshot snapshot) {
        eventSequenceNumber = snapshot.getEventSequenceNumber().toString();
        updatedAt = snapshot.getUpdatedAt();
        deletedAt = snapshot.getDeletedAt();
        aggregateId = snapshot.getAggregateId();
        aggregateState = snapshot.getAggregateState().getTree();

    }
}
