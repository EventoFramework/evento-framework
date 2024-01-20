package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Payload;

import java.io.Serializable;

/**
 * The Handler class is a generic class that represents a handler for payloads of type T. It implements the Serializable interface.
 *
 * @param <T> The type of the payload handled by the handler.
 */
public class Handler<T extends Payload> implements Serializable {
	private T payload;
	private int line;

	/**
	 * Constructs a new Handler object with the specified payload and line number.
	 *
	 * @param payload The payload object.
	 * @param line    The line number where the handler is invoked.
	 */
	public Handler(T payload, int line) {
		this.payload = payload; this.line = line;
	}

	/**
	 * The ServiceCommandHandler class is a handler for service commands.
	 * It extends the Handler class and implements the HasCommandInvocations interface.
	 */
	public Handler() {
	}

	/**
	 * Retrieves the payload object associated with this handler.
	 *
	 * @return The payload object of type T.
	 */
	public T getPayload() {
		return payload;
	}

	/**
	 * Sets the payload object associated with this handler.
	 *
	 * @param payload The payload object of type T.
	 */
	public void setPayload(T payload) {
		this.payload = payload;
	}

	/**
	 * Retrieves the line number where the handler is invoked.
	 *
	 * @return The line number as an integer.
	 */
	public int getLine() {
		return line;
	}

	/**
	 * Sets the line number where the handler is invoked.
	 *
	 * @param line The line number as an integer.
	 */
	public void setLine(int line) {
		this.line = line;
	}
}
