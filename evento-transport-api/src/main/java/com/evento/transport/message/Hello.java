package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;
import java.util.UUID;

public record Hello(
        UUID correlationId,
        byte protocolVersion,
        String bundleId,
        String instanceId,
        String bundleVersion,
        Set<String> capabilities,
        long timestampMs
) implements Message {

    @JsonCreator
    public Hello(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("protocolVersion") byte protocolVersion,
            @JsonProperty("bundleId") String bundleId,
            @JsonProperty("instanceId") String instanceId,
            @JsonProperty("bundleVersion") String bundleVersion,
            @JsonProperty("capabilities") Set<String> capabilities,
            @JsonProperty("timestampMs") long timestampMs
    ) {
        this.correlationId = correlationId;
        this.protocolVersion = protocolVersion;
        this.bundleId = bundleId;
        this.instanceId = instanceId;
        this.bundleVersion = bundleVersion;
        this.capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        this.timestampMs = timestampMs;
    }
}
