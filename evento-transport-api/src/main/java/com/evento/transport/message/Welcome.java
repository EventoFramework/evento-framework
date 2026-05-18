package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;
import java.util.UUID;

public record Welcome(
        UUID correlationId,
        byte protocolVersion,
        String serverInstanceId,
        Set<String> acceptedCapabilities,
        long timestampMs
) implements Message {

    @JsonCreator
    public Welcome(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("protocolVersion") byte protocolVersion,
            @JsonProperty("serverInstanceId") String serverInstanceId,
            @JsonProperty("acceptedCapabilities") Set<String> acceptedCapabilities,
            @JsonProperty("timestampMs") long timestampMs
    ) {
        this.correlationId = correlationId;
        this.protocolVersion = protocolVersion;
        this.serverInstanceId = serverInstanceId;
        this.acceptedCapabilities = acceptedCapabilities == null ? Set.of() : Set.copyOf(acceptedCapabilities);
        this.timestampMs = timestampMs;
    }
}
