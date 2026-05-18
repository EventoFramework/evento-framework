package com.evento.transport.codec;

import com.evento.transport.message.Message;

/**
 * Encodes/decodes wire {@link Message}s.
 *
 * <p>Implementations must produce a byte representation that round-trips exactly
 * across the codec on both sides of the wire. The transport layer is responsible
 * for framing (length-prefix); the codec handles the body only.
 */
public interface Codec {

    /**
     * Serialize {@code message} into a fresh byte array.
     */
    byte[] encode(Message message) throws CodecException;

    /**
     * Deserialize {@code bytes} (offset/length window) into a concrete {@link Message}.
     * Implementations must enforce a type whitelist to prevent gadget-chain attacks.
     */
    Message decode(byte[] bytes, int offset, int length) throws CodecException;

    default Message decode(byte[] bytes) throws CodecException {
        return decode(bytes, 0, bytes.length);
    }
}
