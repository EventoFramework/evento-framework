package com.evento.transport.codec;

import com.evento.transport.message.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

import java.io.IOException;

/**
 * Default {@link Codec} backed by Jackson CBOR. Polymorphic type discrimination is
 * driven by the {@code @JsonTypeInfo}/{@code @JsonSubTypes} annotations on {@link Message},
 * so the wire format is determined entirely by the sealed hierarchy.
 *
 * <p>The whitelist in {@link MessageTypeRegistry} is enforced post-decode: an object
 * whose runtime class is not allowed is rejected with {@link CodecException}. This
 * provides a defense-in-depth against a configuration that registers extra subtypes.
 */
public final class JacksonCborCodec implements Codec {

    private final ObjectMapper mapper;

    public JacksonCborCodec() {
        this(defaultMapper());
    }

    public JacksonCborCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static ObjectMapper defaultMapper() {
        return new ObjectMapper(new CBORFactory())
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public byte[] encode(Message message) {
        if (message == null) {
            throw new CodecException("cannot encode null Message");
        }
        try {
            return mapper.writeValueAsBytes(message);
        } catch (IOException e) {
            throw new CodecException("encode failed for " + message.getClass().getSimpleName(), e);
        }
    }

    @Override
    public Message decode(byte[] bytes, int offset, int length) {
        if (bytes == null || length <= 0) {
            throw new CodecException("empty frame");
        }
        try {
            Object obj = mapper.readValue(bytes, offset, length, Message.class);
            if (obj == null) {
                throw new CodecException("decoded to null");
            }
            if (!MessageTypeRegistry.isAllowed(obj.getClass())) {
                throw new CodecException("disallowed message type: " + obj.getClass().getName());
            }
            return (Message) obj;
        } catch (IOException e) {
            throw new CodecException("decode failed (length=" + length + ")", e);
        }
    }
}
