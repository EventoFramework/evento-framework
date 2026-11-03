package com.evento.server.web.dto;

import com.evento.server.es.snapshot.Snapshot;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;

import java.time.Instant;

@Data
public class SnapshotDTO {

    private String eventSequenceNumber;
    private String aggregateId;
    private Instant updatedAt;
    private Instant deletedAt;
    /** The state's JSON as stored, for the reason in {@link EventDTO#getEvent()}. */
    @JsonRawValue
    private String aggregateState;

    public SnapshotDTO(Snapshot snapshot) {
        eventSequenceNumber = snapshot.getEventSequenceNumber().toString();
        updatedAt = snapshot.getUpdatedAt();
        deletedAt = snapshot.getDeletedAt();
        aggregateId = snapshot.getAggregateId();
        aggregateState = snapshot.getAggregateState().getSerializedObject();
    }
}
