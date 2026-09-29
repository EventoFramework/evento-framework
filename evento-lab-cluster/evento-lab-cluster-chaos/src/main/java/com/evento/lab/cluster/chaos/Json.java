package com.evento.lab.cluster.chaos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private Json() {
    }

    public static JsonNode read(String s) {
        try {
            return MAPPER.readTree(s == null || s.isBlank() ? "{}" : s);
        } catch (Exception e) {
            return MAPPER.createObjectNode().put("_raw", s);
        }
    }

    public static String write(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            throw new HarnessException("json", e);
        }
    }
}
