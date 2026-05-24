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

    private HandshakeProtocol() {}
}
