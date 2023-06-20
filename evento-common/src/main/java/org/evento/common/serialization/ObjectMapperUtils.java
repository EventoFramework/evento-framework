package org.evento.common.serialization;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

public class ObjectMapperUtils {

	private static ObjectMapper instance;


	public synchronized static ObjectMapper getPayloadObjectMapper() {
		if (instance == null)
		{
			PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
					.allowIfSubType("org.evento")
					.allowIfSubType("java.util.ArrayList")
					.allowIfSubType("java.util.HashMap")
					.allowIfSubType("java.util.ImmutableCollections")
					.build();

			var om = new ObjectMapper();
			om.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
			om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			om.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);
			om.setVisibility(om.getSerializationConfig().getDefaultVisibilityChecker()
					.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
					.withGetterVisibility(JsonAutoDetect.Visibility.NONE)
					.withSetterVisibility(JsonAutoDetect.Visibility.NONE)
					.withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
			instance = om;
		}
		return instance;
	}
}
