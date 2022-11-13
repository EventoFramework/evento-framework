package org.eventrails.common.modeling.exceptions;

public class HandlerNotFoundException extends RuntimeException {

	public HandlerNotFoundException() {
	}

	public HandlerNotFoundException(String message) {
		super(message);
	}
}
