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

    /**
     * Sent by a broker to the bundles that negotiated
     * {@link com.evento.transport.HandshakeProtocol#CAPABILITY_HEAD} when the event store has
     * grown (3.0). Body: the new head, an 8-byte big-endian sequence number. Their consumers at
     * the head fetch again at once instead of polling for it.
     */
    public static final String EVENTS_APPENDED = "evento:events-appended";

    private ProtocolNotifications() {}
}
