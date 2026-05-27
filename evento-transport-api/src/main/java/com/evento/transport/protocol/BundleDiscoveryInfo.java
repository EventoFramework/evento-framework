package com.evento.transport.protocol;

import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Body of the {@code evento:bundle-discovery} notification, sent by a bundle
 * after {@code evento:enable} to supply the rich auto-discovery metadata that
 * the server persists to the dashboard database.
 *
 * <p>Kept separate from {@link BundleRegistrationInfo} so that the lean
 * registration frame stays small. The server routes by
 * {@link BundleRegistrationInfo#handlerPayloadTypes()} on the hot path;
 * this record is only needed by {@code AutoDiscoveryService} and
 * {@code CommandBrokerHandler}, which consume it off the event-bus thread.
 *
 * <p>Source-link URL formula (assembled by the server dashboard):
 * <pre>{@code {repositoryUrl}/{component.path}#{linePrefix}{line} }</pre>
 * Example (Bitbucket): {@code .../OrderAggregate.java#lines-42}
 * Example (GitHub):    {@code .../OrderAggregate.java#L42}
 */
public record BundleDiscoveryInfo(
        long bundleVersion,
        String description,
        String detail,
        String repositoryUrl,
        String linePrefix,
        List<RegisteredHandler> handlers,
        Map<String, PayloadDiscoveryInfo> payloadInfo
) {

    /** The {@link com.evento.transport.message.Notification#payloadType()} that carries this record. */
    public static final String PAYLOAD_TYPE = ProtocolNotifications.BUNDLE_DISCOVERY;

    @JsonCreator
    public BundleDiscoveryInfo(
            @JsonProperty("bundleVersion") long bundleVersion,
            @JsonProperty("description")   String description,
            @JsonProperty("detail")        String detail,
            @JsonProperty("repositoryUrl") String repositoryUrl,
            @JsonProperty("linePrefix")    String linePrefix,
            @JsonProperty("handlers")      List<RegisteredHandler> handlers,
            @JsonProperty("payloadInfo")   Map<String, PayloadDiscoveryInfo> payloadInfo
    ) {
        this.bundleVersion = bundleVersion;
        this.description   = description   == null ? "" : description;
        this.detail        = detail        == null ? "" : detail;
        this.repositoryUrl = repositoryUrl == null ? "" : repositoryUrl;
        this.linePrefix    = linePrefix    == null ? "L" : linePrefix;
        this.handlers      = handlers  == null ? List.of() : List.copyOf(handlers);
        this.payloadInfo   = payloadInfo == null ? Map.of() : Map.copyOf(payloadInfo);
    }
}
