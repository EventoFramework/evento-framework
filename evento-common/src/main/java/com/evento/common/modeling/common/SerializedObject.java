package com.evento.common.modeling.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.common.serialization.ObjectMapperUtils;

import java.io.Serializable;

/**
 * Represents a serialized object that can be converted to and from a string representation.
 * The serialized object must implement the Serializable interface.
 * @param <T> the serialized object
 */

public class SerializedObject<T extends Serializable> implements Serializable {

	private static final Logger logger = LogManager.getLogger(SerializedObject.class);
	private String serializedObject;
	private String objectClass;
	/**
	 * The {@link com.evento.common.versioning.Revision} of the class when the object was
	 * serialized (2.6); 0, and absent from the JSON, for an unrevised class and for everything
	 * written before revisions existed.
	 */
	@JsonInclude(JsonInclude.Include.NON_DEFAULT)
	private int revision;

	/**
	 * Represents a serialized object that can be converted to and from a string representation.
	 * The serialized object must implement the Serializable interface.
	 * @param object the serialized object
	 */
	public SerializedObject(T object) {
		try
		{
			this.serializedObject = ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(object);
			if (object != null) {
				this.objectClass = object.getClass().toString();
				this.revision = com.evento.common.versioning.Revisions.of(object.getClass());
			}
		} catch (JsonProcessingException e)
		{
			logger.error("Serialization failed", e);
			this.serializedObject = null;
		}
	}

	/**
	 *
	 */
	public SerializedObject() {
	}

	/**
	 * Retrieves the serialized object as a string.
	 *
	 * @return the serialized object as a string
	 */
	public String getSerializedObject() {
		return serializedObject;
	}

	/**
	 * Sets the serialized object for the SerializedObject class.
	 *
	 * @param serializedObject the serialized object as a string
	 */
	public void setSerializedObject(String serializedObject) {
		this.serializedObject = serializedObject;
	}

	/**
	 * Retrieves the class name of the serialized object.
	 *
	 * @return the class name of the serialized object as a String.
	 */
	public String getObjectClass() {
		return objectClass;
	}

	/**
	 * Sets the class name of the serialized object.
	 *
	 * @param objectClass the class name of the serialized object as a String
	 */
	public void setObjectClass(String objectClass) {
		this.objectClass = objectClass;
	}

	/**
	 * Retrieves the revision of the serialized object's class when it was written.
	 *
	 * @return the revision, 0 when the class has none or the object predates revisions
	 */
	public int getRevision() {
		return revision;
	}

	/**
	 * Sets the revision of the serialized object (an upcaster moved it to another one).
	 *
	 * @param revision the revision
	 */
	public void setRevision(int revision) {
		this.revision = revision;
	}

	/**
	 * Retrieves the deserialized object from the SerializedObject.
	 *
	 * @return the deserialized object, or null if deserialization fails
	 */
	@JsonIgnore
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

	/**
	 * Retrieves the tree structure of the serialized object.
	 *
	 * @return the tree structure of the serialized object as a JsonNode, or null if deserialization fails
	 */
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
