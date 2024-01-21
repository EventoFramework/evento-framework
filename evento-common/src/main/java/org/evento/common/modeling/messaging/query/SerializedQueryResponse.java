package org.evento.common.modeling.messaging.query;

import org.evento.common.modeling.common.SerializedObject;

/**
 * Constructs a new SerializedQueryResponse object with the specified object.
 *
 * @param <T> The Query Response Type
 */
public class SerializedQueryResponse<T extends QueryResponse<?>> extends SerializedObject<T> {
	/**
	 * Constructs a new SerializedQueryResponse object with the specified object.
	 *
	 * @param object the object to be serialized.
     */
	public SerializedQueryResponse(T object) {
		super(object);
	}

	/**
	 * Represents a serialized query response object that can be converted to and from a string representation.
	 * It extends the SerializedObject class.
	 * This class provides a default constructor.
	 */
	public SerializedQueryResponse() {
	}
}
