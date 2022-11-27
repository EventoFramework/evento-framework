package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.messaging.payload.Payload;

import java.io.Serializable;
import java.util.HashMap;

public abstract class Message<T extends Payload> implements Serializable {

	private SerializedPayload<T> serializedPayload;

	private HashMap<String, String> metadata;

	public Message(T payload) {
		this.serializedPayload = new SerializedPayload<>(payload);
	}

	public Message() {
	}

	public T getPayload() {
		return serializedPayload.getObject();
	}

	public void setPayload(T payload) {
		this.serializedPayload = new SerializedPayload<>(payload);
	}

	public SerializedPayload<T> getSerializedPayload() {
		return serializedPayload;
	}

	public void setSerializedPayload(SerializedPayload<T> serializedPayload) {
		this.serializedPayload = serializedPayload;
	}

	public HashMap<String, String> getMetadata() {
		return metadata;
	}

	public void setMetadata(HashMap<String, String> metadata) {
		this.metadata = metadata;
	}

	public String getType() {
		return serializedPayload.getObjectClass();
	}

	public String getPayloadName() {
		var parts = getType().split("\\.");
		return parts[parts.length - 1];
	}

}
