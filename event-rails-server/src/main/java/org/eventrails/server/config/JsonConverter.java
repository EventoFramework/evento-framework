package org.eventrails.server.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eventrails.common.serialization.ObjectMapperUtils;

import javax.persistence.AttributeConverter;

public class JsonConverter implements AttributeConverter<Object, String> {
	@Override
	public String convertToDatabaseColumn(Object attribute) {
		try
		{
			return ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(attribute);
		} catch (JsonProcessingException e)
		{
			return null;
		}
	}

	@Override
	public Object convertToEntityAttribute(String dbData) {
		try
		{
			return ObjectMapperUtils.getPayloadObjectMapper().readValue(dbData, Object.class);
		} catch (JsonProcessingException e)
		{
			return null;
		}
	}
}
