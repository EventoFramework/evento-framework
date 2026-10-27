package com.evento.test;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Objects;

/**
 * Payload equality for the fixtures: {@code equals} when the class defines it, otherwise field by
 * field (payloads seldom override {@code equals}, and the framework compares them by content
 * anyway, since they travel serialized).
 */
final class Payloads {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    private Payloads() {
    }

    static boolean same(Object expected, Object actual) {
        if (expected == null || actual == null) return expected == actual;
        if (expected.getClass() != actual.getClass()) return false;
        if (Objects.equals(expected, actual)) return true;
        return MAPPER.valueToTree(expected).equals(MAPPER.valueToTree(actual));
    }

    static String describe(Object payload) {
        if (payload == null) return "null";
        try {
            return payload.getClass().getSimpleName() + MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return payload.toString();
        }
    }
}
