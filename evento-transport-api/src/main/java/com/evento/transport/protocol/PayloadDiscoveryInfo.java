package com.evento.transport.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Rich metadata about a single payload class, sent inside {@link BundleDiscoveryInfo}.
 *
 * <p>Replaces the bare {@code String[]} that was used in the v1 {@code payloadInfo} map.
 * Fields match the {@code Payload} JPA entity on the server side.
 *
 * @param schema      JSON Schema string (may be {@code "null"} if generation failed)
 * @param domain      Domain tag from {@code @Domain}, or {@code null}
 * @param description Short description from {@code @EventoDescription.value()}, or ""
 * @param detail      Markdown long-form from {@code @EventoDescription.detail()}, or ""
 * @param path        Relative source path, e.g. {@code com/example/order/CreateOrder.java}
 * @param line        Source line of the class declaration (0 if unknown)
 */
public record PayloadDiscoveryInfo(
        String schema,
        String domain,
        String description,
        String detail,
        String path,
        int line
) {
    @JsonCreator
    public PayloadDiscoveryInfo(
            @JsonProperty("schema")      String schema,
            @JsonProperty("domain")      String domain,
            @JsonProperty("description") String description,
            @JsonProperty("detail")      String detail,
            @JsonProperty("path")        String path,
            @JsonProperty("line")        int line
    ) {
        this.schema      = schema;
        this.domain      = domain;
        this.description = description == null ? "" : description;
        this.detail      = detail      == null ? "" : detail;
        this.path        = path        == null ? "" : path;
        this.line        = line;
    }
}
