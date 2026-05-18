package com.evento.transport.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

import java.io.IOException;
import java.util.Set;

/**
 * Default {@link PayloadCodec} using Jackson CBOR. Operates by static target type:
 * the caller specifies the concrete {@code Class<T>} at decode time, so no top-level
 * polymorphic type information is written on the wire.
 *
 * <p>When the caller knows the type up-front, this is the safe and compact choice
 * (no gadget-chain surface, no wire overhead). Polymorphic decoding via a sealed
 * base requires {@code activatePolymorphism()} below, which installs a
 * {@link PolymorphicTypeValidator} restricting deserialization to the whitelisted
 * base classes.
 */
public final class JacksonCborPayloadCodec implements PayloadCodec {

    private final ObjectMapper mapper;

    public JacksonCborPayloadCodec() {
        this(defaultMapper());
    }

    public JacksonCborPayloadCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static ObjectMapper defaultMapper() {
        return new ObjectMapper(new CBORFactory())
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Build a codec that supports polymorphic decode against {@code allowedBases}.
     * The wire output for polymorphic types includes a type discriminator.
     */
    public static JacksonCborPayloadCodec withPolymorphism(Set<Class<?>> allowedBases) {
        var builder = BasicPolymorphicTypeValidator.builder();
        for (Class<?> base : allowedBases) {
            builder.allowIfSubType(base);
        }
        var mapper = defaultMapper()
                .activateDefaultTyping(builder.build(), ObjectMapper.DefaultTyping.NON_FINAL);
        return new JacksonCborPayloadCodec(mapper);
    }

    @Override
    public byte[] encode(Object payload) {
        if (payload == null) {
            return new byte[0];
        }
        try {
            return mapper.writeValueAsBytes(payload);
        } catch (IOException e) {
            throw new CodecException("encode failed for " + payload.getClass().getName(), e);
        }
    }

    @Override
    public <T> T decode(byte[] bytes, Class<T> type) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return mapper.readValue(bytes, type);
        } catch (IOException e) {
            throw new CodecException("decode failed for " + type.getName(), e);
        }
    }
}
