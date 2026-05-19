package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record Reject(
        UUID correlationId,
        String code,
        String reason,
        long timestampMs
) implements Message {

    public static final String CODE_PROTOCOL_VERSION = "PROTOCOL_VERSION";
    public static final String CODE_DUPLICATE_INSTANCE = "DUPLICATE_INSTANCE";
    public static final String CODE_UNKNOWN_BUNDLE = "UNKNOWN_BUNDLE";
    public static final String CODE_AUTH_FAILED = "AUTH_FAILED";
    public static final String CODE_INTERNAL = "INTERNAL";

    @JsonCreator
    public Reject(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("code") String code,
            @JsonProperty("reason") String reason,
            @JsonProperty("timestampMs") long timestampMs
    ) {
        this.correlationId = correlationId;
        this.code = code;
        this.reason = reason;
        this.timestampMs = timestampMs;
    }
}
