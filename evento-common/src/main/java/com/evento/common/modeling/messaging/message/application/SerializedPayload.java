package com.evento.common.modeling.messaging.message.application;

import com.evento.common.modeling.common.SerializedObject;
import com.evento.common.modeling.messaging.payload.Payload;

/**
 * The `SerializedPayload` class is a generic class that represents a serialized payload object.
 * It extends the `SerializedObject` class and provides additional functionalities.
 *
 * @param <T> The type of the payload.
 */
public class SerializedPayload<T extends Payload> extends SerializedObject<T> {

	/**
	 * Represents a serialized payload object that can be converted to and from a string representation.
	 * It extends the SerializedObject class and provides additional functionalities.
	 *
     * @param object the serialized object
     */
	public SerializedPayload(T object) {
		super(object);
	}

	/**
	 * The SerializedPayload class represents a serialized payload object. It extends the SerializedObject class and provides additional functionalities.
	 *
     */
	public SerializedPayload() {
	}
}
