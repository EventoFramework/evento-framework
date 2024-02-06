package com.evento.common.modeling.exceptions;

/**
 * HandlerNotFoundException is an exception class that is thrown when no handler is found for a specific command or query.
 * It extends the RuntimeException class.
 */
public class HandlerNotFoundException extends RuntimeException {

	/**
	 * Constructs a new HandlerNotFoundException with the specified message.
	 *
	 * @param message the detail message
	 */
	public HandlerNotFoundException(String message) {
		super(message);
	}
}
