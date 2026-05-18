package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * RPC request. The transport layer is payload-agnostic: {@code payload} is opaque
 * (typically CBOR-encoded user-domain object) and routing is decided by
 * {@code payloadType} (a stable type name agreed by client/server registries).
 */
public record Request(
        UUID correlationId,
        String sourceBundleId,
        String sourceInstanceId,
        String sourceBundleVersion,
        String payloadType,
        byte[] payload,
        long timeoutMillis,
        long timestampMs
) implements Message {

    @JsonCreator
    public Request(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("sourceBundleId") String sourceBundleId,
            @JsonProperty("sourceInstanceId") String sourceInstanceId,
            @JsonProperty("sourceBundleVersion") String sourceBundleVersion,
            @JsonProperty("payloadType") String payloadType,
            @JsonProperty("payload") byte[] payload,
            @JsonProperty("timeoutMillis") long timeoutMillis,
            @JsonProperty("timestampMs") long timestampMs
    ) {
        this.correlationId = correlationId;
        this.sourceBundleId = sourceBundleId;
        this.sourceInstanceId = sourceInstanceId;
        this.sourceBundleVersion = sourceBundleVersion;
        this.payloadType = payloadType;
        this.payload = payload == null ? new byte[0] : payload;
        this.timeoutMillis = timeoutMillis;
        this.timestampMs = timestampMs;
    }

    public boolean isExpired(long nowMs) {
        return timeoutMillis > 0 && nowMs - timestampMs > timeoutMillis;
    }
}
