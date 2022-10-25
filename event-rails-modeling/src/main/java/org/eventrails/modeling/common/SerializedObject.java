package org.eventrails.modeling.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.eventrails.modeling.messaging.payload.Payload;
import org.eventrails.modeling.utils.ObjectMapperUtils;

import java.io.Serializable;

public class SerializedObject<T extends Serializable> implements Serializable {
	private String serializedObject;
	private String objectClass;

	public SerializedObject(T object) {
		try
		{
			this.serializedObject = ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(object);
			if (object != null)
				this.objectClass = object.getClass().toString();
		} catch (JsonProcessingException e)
		{
			this.serializedObject = "null";
		}
	}

	public SerializedObject() {
	}

	public String getSerializedObject() {
		return serializedObject;
	}

	public void setSerializedObject(String serializedObject) {
		this.serializedObject = serializedObject;
	}

	public String getObjectClass() {
		return objectClass;
	}

	public void setObjectClass(String objectClass) {
		this.objectClass = objectClass;
	}

	@SuppressWarnings("unchecked")
	public T getObject() {
		try
		{
			return (T) ObjectMapperUtils.getPayloadObjectMapper().readValue(serializedObject, Object.class);
		} catch (Exception e)
		{
			return null;
		}
	}

	public JsonNode getTree() {
		try
		{
			return ObjectMapperUtils.getPayloadObjectMapper().readTree(serializedObject);
		} catch (Exception e)
		{
			return null;
		}
	}


}