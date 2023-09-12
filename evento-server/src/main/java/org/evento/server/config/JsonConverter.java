package org.evento.server.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.evento.common.serialization.ObjectMapperUtils;

import javax.persistence.AttributeConverter;
import java.io.IOException;

public class JsonConverter implements AttributeConverter<Object, byte[]> {
    @Override
    public byte[] convertToDatabaseColumn(Object attribute) {
        try {
            return ObjectMapperUtils.getPayloadObjectMapper().writeValueAsBytes(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object convertToEntityAttribute(byte[] dbData) {
		try {
			return ObjectMapperUtils.getPayloadObjectMapper().readValue(dbData, Object.class);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
