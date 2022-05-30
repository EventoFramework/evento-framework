package org.eventrails.parser.model.handler;

import org.eventrails.parser.model.payload.Payload;

public class Handler<T extends Payload> {
	private final T payload;

	public Handler(T payload) {
		this.payload = payload;
	}

	public T getPayload() {
		return payload;
	}
}
