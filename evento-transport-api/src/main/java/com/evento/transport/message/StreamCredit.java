package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * The caller of a streamed reply lets the handler send {@code credits} more frames
 * (2.6), caller → handler, relayed by the broker as is. The caller grants credit as it
 * consumes, so no hop ever holds more than the window of frames in flight for a stream:
 * a slow consumer slows the handler down instead of filling the broker's memory.
 */
public record StreamCredit(
        UUID correlationId,
        int credits,
        long timestampMs
) implements Message {

    @JsonCreator
    public StreamCredit(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("credits") int credits,
            @JsonProperty("timestampMs") long timestampMs
    ) {
        this.correlationId = correlationId;
        this.credits = Math.max(0, credits);
        this.timestampMs = timestampMs;
    }

    public static StreamCredit of(UUID correlationId, int credits) {
        return new StreamCredit(correlationId, credits, System.currentTimeMillis());
    }
}
