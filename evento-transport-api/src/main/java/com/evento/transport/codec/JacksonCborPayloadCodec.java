package com.evento.transport.codec;

import com.fasterxml.jackson.databind.DeserializationFeature;
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

    /**
     * <h2>Why unknown properties are ignored</h2>
     * <p>Protocol payloads evolve by addition — a new field on {@code BundleDiscoveryInfo},
     * {@code RegisteredHandler}, and so on. With {@code FAIL_ON_UNKNOWN_PROPERTIES} enabled,
     * a bundle one version ahead of its broker had its <em>entire</em> payload rejected, and
     * the failure was near-silent: the bundle registered, enabled and consumed normally
     * while its handler metadata simply never appeared in the dashboard. Mixed-version
     * fleets are normal during a rolling upgrade, so that is the wrong default.
     *
     * <p>This is <b>not</b> a weakening of the deserialization hardening. The gadget-chain
     * defence is the {@link PolymorphicTypeValidator} installed by
     * {@link #withPolymorphism(Set)} (and {@code MessageTypeRegistry} on the message codec),
     * which constrains <em>which types may be instantiated</em>. Skipping an unrecognised
     * property on an already-whitelisted target type instantiates nothing — Jackson reads
     * the value and discards it.
     *
     * <p>The complementary direction (an older peer omitting a field a newer one expects) is
     * handled by the payload records themselves, whose {@code @JsonCreator} constructors
     * normalise {@code null} to a default.
     */
    public static ObjectMapper defaultMapper() {
        return new ObjectMapper(new CBORFactory())
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
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
