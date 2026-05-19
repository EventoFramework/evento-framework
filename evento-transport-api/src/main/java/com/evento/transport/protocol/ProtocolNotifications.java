package com.evento.transport.protocol;

/**
 * Reserved {@code payloadType} strings on the {@code evento:*} namespace.
 * These are framework-level notifications recognised by the server/router.
 * Application code MUST NOT register handlers under these names.
 */
public final class ProtocolNotifications {

    /** Sent by a bundle right after handshake to declare its handler types. Body is a {@link BundleRegistrationInfo}. */
    public static final String BUNDLE_REGISTRATION = "evento:bundle-registration";

    /** Sent by a bundle to mark itself routable. No payload. */
    public static final String ENABLE = "evento:enable";

    /** Sent by a bundle to take itself out of routing. No payload. */
    public static final String DISABLE = "evento:disable";

    /** Sent by the server to a bundle to ask it to terminate. No payload. */
    public static final String KILL = "evento:kill";

    private ProtocolNotifications() {}
}
