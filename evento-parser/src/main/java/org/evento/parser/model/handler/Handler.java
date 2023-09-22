package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Payload;

import java.io.Serializable;

public class Handler<T extends Payload> implements Serializable {
	private T payload;
	private int line;

	public Handler(T payload, int line) {
		this.payload = payload; this.line = line;
	}

	public Handler() {
	}

	public T getPayload() {
		return payload;
	}

	public void setPayload(T payload) {
		this.payload = payload;
	}

	public int getLine() {
		return line;
	}

	public void setLine(int line) {
		this.line = line;
	}
}
