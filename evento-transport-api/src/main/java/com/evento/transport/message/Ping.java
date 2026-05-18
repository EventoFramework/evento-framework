package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record Ping(
        UUID correlationId,
        long sequence,
        long timestampMs
) implements Message {

    @JsonCreator
    public Ping(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("sequence") long sequence,
            @JsonProperty("timestampMs") long timestampMs
    ) {
        this.correlationId = correlationId;
        this.sequence = sequence;
        this.timestampMs = timestampMs;
    }
}
