package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record Pong(
        UUID correlationId,
        long sequence,
        long timestampMs,
        long originTimestampMs
) implements Message {

    @JsonCreator
    public Pong(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("sequence") long sequence,
            @JsonProperty("timestampMs") long timestampMs,
            @JsonProperty("originTimestampMs") long originTimestampMs
    ) {
        this.correlationId = correlationId;
        this.sequence = sequence;
        this.timestampMs = timestampMs;
        this.originTimestampMs = originTimestampMs;
    }
}
