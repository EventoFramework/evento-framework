package org.evento.common.modeling.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.serialization.ObjectMapperUtils;

import java.io.Serializable;

/**
 * Represents a serialized object that can be converted to and from a string representation.
 * The serialized object must implement the Serializable interface.
 */

public class SerializedObject<T extends Serializable> implements Serializable {

	private final Logger logger = LogManager.getLogger(this.getClass());
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
			logger.error("Serialization failed", e);
			this.serializedObject = null;
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
			logger.error("Deserialization error", e);
			return null;
		}
	}

	public JsonNode getTree() {
		try
		{
			return ObjectMapperUtils.getPayloadObjectMapper().readTree(serializedObject);
		} catch (Exception e)
		{
			logger.error("Deserialization error", e);
			return null;
		}
	}


}
