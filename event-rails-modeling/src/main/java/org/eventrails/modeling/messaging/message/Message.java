package org.eventrails.modeling.messaging.message;

import org.eventrails.modeling.messaging.payload.Payload;

import java.util.HashMap;

public abstract class Message<T extends Payload> {


	private T payload;

	private HashMap<String, String> metadata;
	public Message(T payload) {
		this.payload = payload;
	}

	public Message(){}

	public T getPayload() {
		return payload;
	}

	public void setPayload(T payload) {
		this.payload = payload;
	}

	public Class<? extends Payload> getPayloadClass(){
		return payload.getClass();
	}

	public HashMap<String, String> getMetadata() {
		return metadata;
	}

	public void setMetadata(HashMap<String, String> metadata) {
		this.metadata = metadata;
	}

	public String getType(){
		return getPayloadClass().getSimpleName();
	}

}
