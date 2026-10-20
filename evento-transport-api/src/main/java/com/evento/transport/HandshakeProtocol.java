package com.evento.transport;

/**
 * Wire protocol constants. Versioning lives here so any reader of either side
 * can find the source of truth.
 *
 * <p>Backwards compatibility is opt-in: peers exchange {@code protocolVersion} in
 * {@link com.evento.transport.message.Hello} / {@link com.evento.transport.message.Welcome}
 * and the server may reject a Hello with {@link com.evento.transport.message.Reject}
 * code {@code PROTOCOL_VERSION} if the proposed version is not supported.
 */
public final class HandshakeProtocol {

    public static final byte PROTOCOL_VERSION = 0x02;

    /** Maximum size of a single wire frame (chunk). Messages larger than this are automatically chunked. */
    public static final int MAX_FRAME_LENGTH = 16 * 1024 * 1024;

    /** Standardised capability flags advertised in Hello/Welcome.acceptedCapabilities. */
    public static final String CAPABILITY_LZ4 = "lz4";
    public static final String CAPABILITY_PING_PONG = "ping-pong";

    /**
     * The peer exchanges streamed replies ({@code StreamData}, {@code StreamCredit},
     * {@code StreamCancel}, {@code StreamRedirect}, 3.0). Stream messages are only sent to
     * a peer that negotiated it: one that did not cannot decode them. A broker accepts it
     * from every bundle that offers it; a bundle streams only on a session where it was
     * accepted.
     */
    public static final String CAPABILITY_STREAM = "stream:1";

    /**
     * The broker keeps the revision of every payload it stores or forwards (3.0). An older
     * broker decodes a payload envelope with classes that have no revision and re-encodes it
     * without one: a payload written at a later revision would then be stored as revision 0
     * and upcast again on every read. A bundle sends payloads of a revised class only on a
     * session where this was accepted. A broker accepts it from every bundle that offers it.
     */
    public static final String CAPABILITY_REVISION = "revision:1";

    /**
     * The broker stores several events from one aggregate command, atomically (3.0,
     * {@code DomainEvents}). An older broker would ignore the events before the last one and
     * store that one alone: a bundle answers with several events only on a session where this
     * was accepted. A broker accepts it from every bundle that offers it.
     */
    public static final String CAPABILITY_EVENTS = "events:1";

    /**
     * Prefix of the capability a bundle uses to say where it runs:
     * {@code "zone:EU"}. It rides in {@code Hello.capabilities} rather than a
     * field of its own so that no wire type changes and a peer that knows
     * nothing about zones reads it as one more capability it does not share.
     * The server reads it for locality-aware handler selection.
     */
    public static final String CAPABILITY_ZONE_PREFIX = "zone:";

    /** The capability string that advertises {@code zone}. */
    public static String zoneCapability(String zone) {
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("zone must not be blank");
        }
        return CAPABILITY_ZONE_PREFIX + zone.trim();
    }

    /**
     * The zone advertised in {@code capabilities}, or null when there is none.
     * A blank value counts as none. With more than one, the lexically first
     * wins so the answer does not depend on set iteration order.
     */
    public static String zoneOf(java.util.Collection<String> capabilities) {
        if (capabilities == null) return null;
        String found = null;
        for (String cap : capabilities) {
            if (cap == null || !cap.startsWith(CAPABILITY_ZONE_PREFIX)) continue;
            String zone = cap.substring(CAPABILITY_ZONE_PREFIX.length()).trim();
            if (zone.isEmpty()) continue;
            if (found == null || zone.compareTo(found) < 0) found = zone;
        }
        return found;
    }

    private HandshakeProtocol() {}
}
