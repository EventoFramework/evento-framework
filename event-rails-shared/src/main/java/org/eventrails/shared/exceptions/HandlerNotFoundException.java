package org.eventrails.shared.exceptions;

public class HandlerNotFoundException extends RuntimeException {

	public HandlerNotFoundException() {
	}

	public HandlerNotFoundException(String message) {
		super(message);
	}
}
