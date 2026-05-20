package com.evento.transport.protocol;

import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Body of the {@code evento:bundle-registration} notification that a bundle
 * sends immediately after a successful handshake. It announces both the lean
 * routing information ({@link #handlerPayloadTypes()} — what the server needs
 * to pick destinations) and the rich auto-discovery information
 * ({@link #handlers()} + {@link #payloadInfo()} — what the server needs to
 * persist component / handler / payload metadata to the dashboard database).
 *
 * <p>The split is deliberate: the {@code MessageRouter} only reads the lean
 * fields on the hot path, while {@code AutoDiscoveryService} consumes the
 * rich fields off the event-bus thread when a {@code BundleRegistered} event
 * is fired.
 *
 * <p>Carried as opaque CBOR bytes inside
 * {@link com.evento.transport.message.Notification#payload()}.
 */
public record BundleRegistrationInfo(
        long bundleVersion,
        List<String> handlerPayloadTypes,
        List<RegisteredHandler> handlers,
        Map<String, String[]> payloadInfo
) {

    /** The {@link com.evento.transport.message.Notification#payloadType()} that carries this record. */
    public static final String PAYLOAD_TYPE = ProtocolNotifications.BUNDLE_REGISTRATION;

    @JsonCreator
    public BundleRegistrationInfo(
            @JsonProperty("bundleVersion") long bundleVersion,
            @JsonProperty("handlerPayloadTypes") List<String> handlerPayloadTypes,
            @JsonProperty("handlers") List<RegisteredHandler> handlers,
            @JsonProperty("payloadInfo") Map<String, String[]> payloadInfo
    ) {
        this.bundleVersion = bundleVersion;
        this.handlerPayloadTypes = handlerPayloadTypes == null ? List.of() : List.copyOf(handlerPayloadTypes);
        this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
        this.payloadInfo = payloadInfo == null ? Map.of() : Map.copyOf(payloadInfo);
    }

    /** Convenience for callers that only need the lean routing fields. */
    public static BundleRegistrationInfo lean(long bundleVersion, List<String> handlerPayloadTypes) {
        return new BundleRegistrationInfo(bundleVersion, handlerPayloadTypes, List.of(), Map.of());
    }
}
