package com.evento.server.bus.v2.handshake;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Body of the {@code evento:bundle-registration} notification that a bundle
 * sends immediately after a successful handshake. It announces which payload
 * types the bundle can handle plus loose per-payload metadata (timeouts, locks,
 * compatibility info — kept as a free-form map so it can evolve without a wire
 * break).
 *
 * <p>Carried as opaque CBOR bytes inside
 * {@link com.evento.transport.message.Notification#payload()}, so this record
 * is part of the {@code evento-server} contract with cooperating bundles, not
 * of the wire-level {@code Message} hierarchy.
 */
public record BundleRegistrationInfo(
        long bundleVersion,
        List<String> handlerPayloadTypes,
        Map<String, Map<String, Object>> payloadMetadata
) {

    public static final String PAYLOAD_TYPE = "evento:bundle-registration";

    @JsonCreator
    public BundleRegistrationInfo(
            @JsonProperty("bundleVersion") long bundleVersion,
            @JsonProperty("handlerPayloadTypes") List<String> handlerPayloadTypes,
            @JsonProperty("payloadMetadata") Map<String, Map<String, Object>> payloadMetadata
    ) {
        this.bundleVersion = bundleVersion;
        this.handlerPayloadTypes = handlerPayloadTypes == null ? List.of() : List.copyOf(handlerPayloadTypes);
        this.payloadMetadata = payloadMetadata == null ? Map.of() : Map.copyOf(payloadMetadata);
    }
}
