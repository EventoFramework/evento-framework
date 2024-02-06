package org.evento.server.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.evento.common.serialization.ObjectMapperUtils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.io.Serializable;

@Converter
public class JsonConverter implements AttributeConverter<Serializable, String> {
    @Override
    public String convertToDatabaseColumn(Serializable attribute) {
        try {
            return ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Serializable convertToEntityAttribute(String dbData) {
		try {
			return ObjectMapperUtils.getPayloadObjectMapper().readValue(dbData, Serializable.class);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
