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
 *
 * <p>Field name is {@code failure} rather than {@code error} to avoid a Jackson
 * deserialization conflict with the record accessor {@code error()}: when a static
 * factory method named {@code error(UUID, ResponseError)} is also present, Jackson's
 * accessor scan picks the static method up as a property getter for {@code error}
 * and the field deserialization silently misses the value.
 */
public record Response(
        UUID correlationId,
        String payloadType,
        byte[] payload,
        ResponseError failure,
        long timestampMs
) implements Message {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Response(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("payloadType") String payloadType,
            @JsonProperty("payload") byte[] payload,
            @JsonProperty("failure") ResponseError failure,
            @JsonProperty("timestampMs") long timestampMs
    ) {
        this.correlationId = correlationId;
        this.payloadType = payloadType;
        this.payload = payload;
        this.failure = failure;
        this.timestampMs = timestampMs;
    }

    /** Convenience accessor kept for callers that grew up with {@code error()}. */
    @JsonIgnore
    public ResponseError error() { return failure; }

    @JsonIgnore
    public boolean isError() {
        return failure != null;
    }

    public static Response success(UUID correlationId, String payloadType, byte[] payload) {
        return new Response(correlationId, payloadType, payload, null, System.currentTimeMillis());
    }

    public static Response failure(UUID correlationId, ResponseError failure) {
        return new Response(correlationId, null, null, failure, System.currentTimeMillis());
    }
}
