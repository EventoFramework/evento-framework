package com.evento.transport.codec;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * End-to-end compression of reply payloads ({@code Response.payload},
 * {@code StreamData.payload}).
 *
 * <p>Compression is negotiated per request, never per hop: a caller lists the encodings
 * it can decode in {@code Request.acceptEncodings}, the handler compresses its reply only
 * when the caller asked and the payload is large enough to gain from it, and the broker
 * forwards the bytes as they are. It stays payload-agnostic and zero-copy, and an
 * old peer on either end simply never asks or never compresses.
 *
 * <p>An encoded payload is self-describing:
 * {@code [0xFF 'E' 'Z' 0x01][original length: 4 bytes BE][zlib data]}. No CBOR data
 * item can start with {@code 0xFF} (the "break" code), so an Evento payload can never be
 * mistaken for an encoded one; the receiver still decodes only when it asked for it.
 * The marker in the payload (rather than a field of the reply) also survives a broker
 * that re-encodes a reply it decoded with an older schema (a dedup replay).
 *
 * <p>DEFLATE at its fastest level in the zlib format (its Adler-32 checksum catches a
 * corrupt payload), from the JDK: no native library, no dependency.
 */
public final class PayloadEncoding {

    /** The {@code acceptEncodings} token for DEFLATE. */
    public static final String DEFLATE = "deflate";

    /** Replies below this many bytes are sent as they are (default for {@code threshold}). */
    public static final int DEFAULT_THRESHOLD = 32 * 1024;

    /** Bound on a decoded payload: a compressed bomb is refused, not inflated. */
    public static final long DEFAULT_MAX_DECODED_BYTES = 512L * 1024 * 1024;

    private static final byte[] MAGIC = {(byte) 0xFF, 'E', 'Z', 0x01};
    private static final int HEADER = MAGIC.length + 4;

    private PayloadEncoding() {}

    /**
     * {@code payload} DEFLATE-encoded when it is at least {@code threshold} bytes and
     * encoding saves at least an eighth of it; otherwise {@code payload} itself.
     * A non-positive threshold disables encoding.
     */
    public static byte[] deflateIfWorthwhile(byte[] payload, int threshold) {
        if (payload == null || threshold <= 0 || payload.length < threshold) return payload;
        var deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(payload);
            deflater.finish();
            int budget = payload.length - payload.length / 8;
            var out = new byte[HEADER + budget];
            int written = HEADER;
            while (!deflater.finished()) {
                if (written == out.length) return payload;  // not worth it
                written += deflater.deflate(out, written, out.length - written);
            }
            System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
            ByteBuffer.wrap(out, MAGIC.length, 4).putInt(payload.length);
            return java.util.Arrays.copyOf(out, written);
        } finally {
            deflater.end();
        }
    }

    /** Whether {@code payload} carries the encoded-payload marker. */
    public static boolean isEncoded(byte[] payload) {
        if (payload == null || payload.length < HEADER) return false;
        for (int i = 0; i < MAGIC.length; i++) {
            if (payload[i] != MAGIC[i]) return false;
        }
        return true;
    }

    /** {@link #decode(byte[], long)} with {@link #DEFAULT_MAX_DECODED_BYTES}. */
    public static byte[] decode(byte[] payload) {
        return decode(payload, DEFAULT_MAX_DECODED_BYTES);
    }

    /**
     * The original bytes of an encoded payload, or {@code payload} itself when it is not
     * encoded.
     *
     * @throws CodecException when the payload is corrupt or would decode to more than
     *                        {@code maxDecodedBytes}
     */
    public static byte[] decode(byte[] payload, long maxDecodedBytes) {
        if (!isEncoded(payload)) return payload;
        int originalLength = ByteBuffer.wrap(payload, MAGIC.length, 4).getInt();
        if (originalLength < 0 || originalLength > maxDecodedBytes) {
            throw new CodecException("encoded payload declares " + originalLength
                    + " bytes, above the bound of " + maxDecodedBytes);
        }
        var inflater = new Inflater();
        try {
            inflater.setInput(payload, HEADER, payload.length - HEADER);
            var out = new byte[originalLength];
            int read = 0;
            while (read < originalLength && !inflater.finished()) {
                int n = inflater.inflate(out, read, originalLength - read);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                read += n;
            }
            if (read == originalLength && !inflater.finished()) {
                // The stream end (and its checksum) may still be ahead of a full buffer.
                if (inflater.inflate(new byte[1]) != 0) read++;
            }
            if (read != originalLength || !inflater.finished()) {
                throw new CodecException("encoded payload is corrupt: decoded " + read + " of "
                        + originalLength + " bytes");
            }
            return out;
        } catch (DataFormatException e) {
            throw new CodecException("encoded payload is corrupt", e);
        } finally {
            inflater.end();
        }
    }
}
