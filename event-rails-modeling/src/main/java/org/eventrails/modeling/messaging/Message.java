package org.eventrails.modeling.messaging;

import org.eventrails.modeling.messaging.payload.Payload;

public abstract class Message<T extends Payload> {


	private T payload;
	public Message(T payload) {
		this.payload = payload;
	}

	public T getPayload() {
		return payload;
	}

	public void setPayload(T payload) {
		this.payload = payload;
	}

}
