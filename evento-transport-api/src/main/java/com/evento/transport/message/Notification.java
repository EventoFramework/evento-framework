package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * One-way notification (no response expected). Used for broadcast events,
 * enable/disable signals, etc. Payload is opaque bytes (typically CBOR).
 */
public record Notification(
        UUID correlationId,
        String payloadType,
        byte[] payload,
        long timestampMs
) implements Message {

    @JsonCreator
    public Notification(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("payloadType") String payloadType,
            @JsonProperty("payload") byte[] payload,
            @JsonProperty("timestampMs") long timestampMs
    ) {
        this.correlationId = correlationId;
        this.payloadType = payloadType;
        this.payload = payload == null ? new byte[0] : payload;
        this.timestampMs = timestampMs;
    }
}
