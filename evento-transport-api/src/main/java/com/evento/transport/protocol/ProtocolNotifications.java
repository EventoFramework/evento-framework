package com.evento.transport.protocol;

/**
 * Reserved {@code payloadType} strings on the {@code evento:*} namespace.
 * These are framework-level notifications recognised by the server/router.
 * Application code MUST NOT register handlers under these names.
 */
public final class ProtocolNotifications {

    /** Sent by a bundle right after handshake to declare its handler types. Body is a {@link BundleRegistrationInfo}. */
    public static final String BUNDLE_REGISTRATION = "evento:bundle-registration";

    /** Sent by a bundle after enable to supply rich auto-discovery metadata. Body is a {@link BundleDiscoveryInfo}. */
    public static final String BUNDLE_DISCOVERY = "evento:bundle-discovery";

    /** Sent by a bundle to mark itself routable. No payload. */
    public static final String ENABLE = "evento:enable";

    /** Sent by a bundle to take itself out of routing. No payload. */
    public static final String DISABLE = "evento:disable";

    private ProtocolNotifications() {}
}
