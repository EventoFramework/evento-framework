package com.evento.server.web;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Structural parser for consumer ids.
 *
 * <p>Bundles build a consumer id as {@code bundleId_componentName_version_context}
 * (see {@code ProjectorEngine}, {@code SagaEngine}, {@code ObserverEngine}). The
 * separator is also a legal character in every part: bundle ids ({@code query_eu}),
 * and above all contexts ({@code SUD_AMERICA}, {@code AUSTRALIA_NZ}). Splitting on
 * {@code _} and taking fixed indexes showed {@code SUD_AMERICA} as {@code SUD}. The
 * server knows the bundle id and component name of every consumer row, so it strips
 * that known prefix and splits only the remainder, where the version is an integer
 * and everything after it is the context.
 */
public final class ConsumerIds {

    /** {@code version_context}: an integer version, then a context that may itself contain {@code _}. */
    private static final Pattern VERSION_CONTEXT = Pattern.compile("(-?\\d+)_(.*)", Pattern.DOTALL);

    public record Parts(String version, String context) {}

    private ConsumerIds() {}

    /**
     * @return the version and context of {@code consumerId}, or empty when the id does not
     *         have the {@code bundleId_componentName_version_context} shape
     */
    public static Optional<Parts> parse(String consumerId, String bundleId, String componentName) {
        if (consumerId == null) return Optional.empty();
        String remainder = null;
        if (bundleId != null && componentName != null) {
            var prefix = bundleId + "_" + componentName + "_";
            if (consumerId.startsWith(prefix)) remainder = consumerId.substring(prefix.length());
        }
        if (remainder == null && componentName != null) {
            // The component row can point at another bundle than the one that built this id
            // (component names are global in the catalog); anchor on the component name alone.
            var marker = "_" + componentName + "_";
            int at = consumerId.indexOf(marker);
            if (at >= 0) remainder = consumerId.substring(at + marker.length());
        }
        if (remainder == null) return Optional.empty();
        var m = VERSION_CONTEXT.matcher(remainder);
        if (!m.matches()) return Optional.empty();
        return Optional.of(new Parts(m.group(1), m.group(2)));
    }
}
