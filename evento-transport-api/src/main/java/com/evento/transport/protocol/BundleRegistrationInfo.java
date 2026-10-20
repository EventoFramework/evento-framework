package com.evento.transport.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Body of the {@code evento:bundle-registration} notification that a bundle
 * sends immediately after a successful handshake. Contains only the lean
 * routing information ({@link #handlerPayloadTypes()}) needed by the server to
 * build its routing table.
 *
 * <p>Rich auto-discovery metadata (component list, payload schemas) is
 * carried by the separate {@link BundleDiscoveryInfo} / {@code evento:bundle-discovery}
 * notification that the bundle sends after {@code evento:enable}, so that the
 * lean registration frame stays small and never hits the Netty frame-length limit.
 *
 * <p>{@code queryPayloadTypes} (additive, 3.0) is the subset of
 * {@code handlerPayloadTypes} answered by read-only, idempotent handlers
 * ({@code @QueryHandler}s). The broker routes them "straight": no exactly-once
 * dedup slot, no cached response, no reconnect buffering — a query that is lost
 * is simply asked again. A bundle that predates the field sends none, and its
 * queries keep the old (cached) treatment.
 */
public record BundleRegistrationInfo(
        long bundleVersion,
        List<String> handlerPayloadTypes,
        List<String> queryPayloadTypes
) {

    /** The {@link com.evento.transport.message.Notification#payloadType()} that carries this record. */
    public static final String PAYLOAD_TYPE = ProtocolNotifications.BUNDLE_REGISTRATION;

    @JsonCreator
    public BundleRegistrationInfo(
            @JsonProperty("bundleVersion") long bundleVersion,
            @JsonProperty("handlerPayloadTypes") List<String> handlerPayloadTypes,
            @JsonProperty("queryPayloadTypes") List<String> queryPayloadTypes
    ) {
        this.bundleVersion = bundleVersion;
        this.handlerPayloadTypes = handlerPayloadTypes == null ? List.of() : List.copyOf(handlerPayloadTypes);
        this.queryPayloadTypes = queryPayloadTypes == null ? List.of() : List.copyOf(queryPayloadTypes);
    }

    /** Pre-3.0 shape: no payload type is declared idempotent. */
    public BundleRegistrationInfo(long bundleVersion, List<String> handlerPayloadTypes) {
        this(bundleVersion, handlerPayloadTypes, null);
    }

    /** Convenience factory — equivalent to the 2-arg constructor. */
    public static BundleRegistrationInfo lean(long bundleVersion, List<String> handlerPayloadTypes) {
        return new BundleRegistrationInfo(bundleVersion, handlerPayloadTypes);
    }
}
