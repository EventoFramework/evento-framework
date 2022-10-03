package org.eventrails.modeling.messaging.message;

import org.eventrails.modeling.messaging.payload.Payload;

public abstract class Message<T extends Payload> {


	private T payload;
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

	public String getType(){
		return getPayloadClass().getSimpleName();
	}

}
