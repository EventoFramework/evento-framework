package org.eventrails.common.modeling.messaging.message.application;

import org.eventrails.common.modeling.messaging.payload.Payload;
import org.eventrails.common.modeling.common.SerializedObject;

public class SerializedPayload<T extends Payload> extends SerializedObject<T> {

	public SerializedPayload(T object) {
		super(object);
	}

	public SerializedPayload() {
	}
}
