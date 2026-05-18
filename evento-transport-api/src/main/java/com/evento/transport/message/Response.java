package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * RPC response paired with a {@link Request} via {@code correlationId}.
 *
 * <p>Either {@code payload} or {@code error} is set, never both. The transport layer
 * treats {@code payload} as opaque bytes — the recipient deserializes it locally.
 */
public record Response(
        UUID correlationId,
        String payloadType,
        byte[] payload,
        ResponseError error,
        long timestampMs
) implements Message {

    @JsonCreator
    public Response(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("payloadType") String payloadType,
            @JsonProperty("payload") byte[] payload,
            @JsonProperty("error") ResponseError error,
            @JsonProperty("timestampMs") long timestampMs
    ) {
        this.correlationId = correlationId;
        this.payloadType = payloadType;
        this.payload = payload;
        this.error = error;
        this.timestampMs = timestampMs;
    }

    @JsonIgnore
    public boolean isError() {
        return error != null;
    }

    public static Response ok(UUID correlationId, String payloadType, byte[] payload) {
        return new Response(correlationId, payloadType, payload, null, System.currentTimeMillis());
    }

    public static Response error(UUID correlationId, ResponseError error) {
        return new Response(correlationId, null, null, error, System.currentTimeMillis());
    }
}
