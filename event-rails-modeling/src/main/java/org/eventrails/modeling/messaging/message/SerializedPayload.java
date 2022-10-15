package org.eventrails.modeling.messaging.message;

import org.eventrails.modeling.messaging.payload.Payload;
import org.eventrails.modeling.common.SerializedObject;

public class SerializedPayload<T extends Payload> extends SerializedObject<T> {

	public SerializedPayload(T object) {
		super(object);
	}

	public SerializedPayload() {
	}
}
