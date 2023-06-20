package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.common.SerializedObject;
import org.evento.common.modeling.messaging.payload.Payload;

public class SerializedPayload<T extends Payload> extends SerializedObject<T> {

	public SerializedPayload(T object) {
		super(object);
	}

	public SerializedPayload() {
	}
}
