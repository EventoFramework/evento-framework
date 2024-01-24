package org.evento.common.modeling.messaging.payload;

import java.util.HashMap;

/**
 * The Invocation class extends the Payload class and represents an invocation of a method. It stores the method arguments
 * in a HashMap.
 */
public class Invocation implements Payload {

	private HashMap<String, Object> arguments = new HashMap<>();

	/**
	 * The `Invocation` class represents an invocation of a method. It stores the method arguments in a HashMap.
	 */
	public Invocation() {
	}

	/**
	 * Retrieves the arguments stored in the HashMap.
	 *
	 * @return the HashMap containing the method arguments
	 */
	public HashMap<String, Object> getArguments() {
		return arguments;
	}

	/**
	 * Sets the arguments for the invocation.
	 *
	 * @param arguments a HashMap containing the method arguments
	 */
	public void setArguments(HashMap<String, Object> arguments) {
		this.arguments = arguments;
	}
}
