package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Stops a streamed reply before its end (3.0): sent by the caller when it closes the
 * stream early, and by the broker to the handler when the caller went away. The handler
 * stops producing and closes its source; frames already on their way are discarded.
 */
public record StreamCancel(
        UUID correlationId,
        String reason,
        long timestampMs
) implements Message {

    @JsonCreator
    public StreamCancel(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("reason") String reason,
            @JsonProperty("timestampMs") long timestampMs
    ) {
        this.correlationId = correlationId;
        this.reason = reason == null ? "" : reason;
        this.timestampMs = timestampMs;
    }

    public static StreamCancel of(UUID correlationId, String reason) {
        return new StreamCancel(correlationId, reason, System.currentTimeMillis());
    }
}
