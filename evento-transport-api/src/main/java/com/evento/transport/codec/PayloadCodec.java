package com.evento.transport.codec;

/**
 * Serializes user domain payloads (commands, events, queries) into the opaque
 * {@code byte[]} slot carried by {@link com.evento.transport.message.Request},
 * {@link com.evento.transport.message.Response}, and
 * {@link com.evento.transport.message.Notification}.
 *
 * <p>This is a separate concern from the wire {@link Codec}, which only deals with
 * the sealed {@code Message} hierarchy. Each bundle/server owns its own
 * {@code PayloadCodec} with a domain-specific type whitelist.
 */
public interface PayloadCodec {

    byte[] encode(Object payload) throws CodecException;

    <T> T decode(byte[] bytes, Class<T> type) throws CodecException;

    default <T> T decode(byte[] bytes, int offset, int length, Class<T> type) throws CodecException {
        if (offset == 0 && length == bytes.length) {
            return decode(bytes, type);
        }
        byte[] copy = new byte[length];
        System.arraycopy(bytes, offset, copy, 0, length);
        return decode(copy, type);
    }
}
