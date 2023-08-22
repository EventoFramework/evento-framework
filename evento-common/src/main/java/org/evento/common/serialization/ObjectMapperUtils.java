package org.evento.common.serialization;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.evento.common.modeling.messaging.payload.Payload;
import org.evento.common.modeling.messaging.query.QueryResponse;
import org.evento.common.modeling.messaging.query.SerializedQueryResponse;
import org.evento.common.modeling.state.AggregateState;

import java.util.ArrayList;
import java.util.HashMap;

public class ObjectMapperUtils {

	private static ObjectMapper instance;


	public synchronized static ObjectMapper getPayloadObjectMapper() {
		if (instance == null)
		{
			PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
					.allowIfSubType(Payload.class)
					.allowIfSubType(QueryResponse.class)
					.allowIfSubType(AggregateState.class)
					.allowIfSubType(SerializedQueryResponse.class)
					.allowIfSubType(ArrayList.class)
					.allowIfSubType(HashMap.class)
					.allowIfSubType("java.util.ImmutableCollections")
					.allowIfSubType("org.evento")
					.build();

			var om = new ObjectMapper();
			om.registerModule(new JavaTimeModule());
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
