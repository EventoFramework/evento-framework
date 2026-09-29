package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;
import java.util.UUID;

/**
 * The broker's reply to an accepted {@link Hello}.
 *
 * <p>{@code clusterName} is additive (2.5): the logical name of the cluster the
 * broker belongs to ({@code evento.cluster.name} on the server). It exists so a
 * bundle configured with several broker endpoints can still refuse a foreign
 * broker: a single expected {@code serverInstanceId} cannot express "any of the
 * three brokers of my cluster". A broker that predates the field sends nothing,
 * which decodes as the empty string — "not announced" — never as {@code null}.
 */
public record Welcome(
        UUID correlationId,
        byte protocolVersion,
        String serverInstanceId,
        Set<String> acceptedCapabilities,
        long timestampMs,
        String clusterName
) implements Message {

    @JsonCreator
    public Welcome(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("protocolVersion") byte protocolVersion,
            @JsonProperty("serverInstanceId") String serverInstanceId,
            @JsonProperty("acceptedCapabilities") Set<String> acceptedCapabilities,
            @JsonProperty("timestampMs") long timestampMs,
            @JsonProperty("clusterName") String clusterName
    ) {
        this.correlationId = correlationId;
        this.protocolVersion = protocolVersion;
        this.serverInstanceId = serverInstanceId;
        this.acceptedCapabilities = acceptedCapabilities == null ? Set.of() : Set.copyOf(acceptedCapabilities);
        this.timestampMs = timestampMs;
        this.clusterName = clusterName == null ? "" : clusterName;
    }

    /** Pre-2.5 shape: a broker that does not announce a cluster name. */
    public Welcome(UUID correlationId, byte protocolVersion, String serverInstanceId,
                   Set<String> acceptedCapabilities, long timestampMs) {
        this(correlationId, protocolVersion, serverInstanceId, acceptedCapabilities, timestampMs, null);
    }
}
