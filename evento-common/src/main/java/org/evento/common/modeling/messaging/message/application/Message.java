package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.messaging.payload.Payload;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;

public abstract class Message<T extends Payload> implements Serializable {

	private SerializedPayload<T> serializedPayload;

	private long timestamp;

	private Metadata metadata;

	public Message(T payload) {
		this.serializedPayload = new SerializedPayload<>(payload);
		this.timestamp = Instant.now().toEpochMilli();
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

	public Metadata getMetadata() {
		return metadata;
	}

	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}

	public String getType() {
		return serializedPayload.getObjectClass();
	}

	public String getPayloadName() {
		var parts = getType().split("\\.");
		return parts[parts.length - 1];
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
}
