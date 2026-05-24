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
 */
public record BundleRegistrationInfo(
        long bundleVersion,
        List<String> handlerPayloadTypes
) {

    /** The {@link com.evento.transport.message.Notification#payloadType()} that carries this record. */
    public static final String PAYLOAD_TYPE = ProtocolNotifications.BUNDLE_REGISTRATION;

    @JsonCreator
    public BundleRegistrationInfo(
            @JsonProperty("bundleVersion") long bundleVersion,
            @JsonProperty("handlerPayloadTypes") List<String> handlerPayloadTypes
    ) {
        this.bundleVersion = bundleVersion;
        this.handlerPayloadTypes = handlerPayloadTypes == null ? List.of() : List.copyOf(handlerPayloadTypes);
    }

    /** Convenience factory — equivalent to the 2-arg constructor. */
    public static BundleRegistrationInfo lean(long bundleVersion, List<String> handlerPayloadTypes) {
        return new BundleRegistrationInfo(bundleVersion, handlerPayloadTypes);
    }
}
