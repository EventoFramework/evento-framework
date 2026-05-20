package com.evento.common.admin;

import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;

/**
 * CBOR codec for {@link EventoRequest} / {@link EventoResponse} carried as the
 * opaque payload of a v2 {@code Request} / {@code Response} under the
 * {@code evento:server-admin-request} payloadType.
 *
 * <p>Both ends of the wire (server side, when {@code BusFacade.forward(...)}
 * encodes an outgoing admin request; bundle side, when its admin handler
 * decodes and replies) must use the same Jackson polymorphic configuration:
 * the {@code body: Serializable} field carries arbitrary subtypes
 * ({@code ConsumerFetchStatusRequestMessage}, {@code ExceptionWrapper}, …) and
 * we need type discriminators on the wire so the receiver can reconstruct the
 * concrete type. The whitelist mirrors the v1 wire's
 * {@code ObjectMapperUtils.getPayloadObjectMapper} (subtypes of
 * {@code Serializable} or anything under {@code com.evento.*}) so existing
 * payload classes keep flowing without per-class registration.
 */
public final class AdminPayloadCodec {

    private final ObjectMapper mapper;

    public AdminPayloadCodec() {
        this(defaultMapper());
    }

    AdminPayloadCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static ObjectMapper defaultMapper() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Serializable.class)
                .allowIfSubType("com.evento.")
                .build();
        var om = new ObjectMapper(new CBORFactory())
                .registerModule(new JavaTimeModule());
        om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);
        om.setVisibility(om.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        return om;
    }

    public byte[] encodeRequest(EventoRequest request) {
        try {
            return mapper.writeValueAsBytes(request);
        } catch (IOException e) {
            throw new UncheckedIOException("admin request encode failed", e);
        }
    }

    public EventoRequest decodeRequest(byte[] bytes) {
        try {
            return mapper.readValue(bytes, EventoRequest.class);
        } catch (IOException e) {
            throw new UncheckedIOException("admin request decode failed", e);
        }
    }

    public byte[] encodeResponse(EventoResponse response) {
        try {
            return mapper.writeValueAsBytes(response);
        } catch (IOException e) {
            throw new UncheckedIOException("admin response encode failed", e);
        }
    }

    public EventoResponse decodeResponse(byte[] bytes) {
        try {
            return mapper.readValue(bytes, EventoResponse.class);
        } catch (IOException e) {
            throw new UncheckedIOException("admin response decode failed", e);
        }
    }
}
