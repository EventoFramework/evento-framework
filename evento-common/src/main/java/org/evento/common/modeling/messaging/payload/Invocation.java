package org.evento.common.modeling.messaging.payload;

import java.util.HashMap;

public class Invocation extends Payload {

	private HashMap<String, Object> arguments = new HashMap<>();

	public Invocation() {
	}

	public HashMap<String, Object> getArguments() {
		return arguments;
	}

	public void setArguments(HashMap<String, Object> arguments) {
		this.arguments = arguments;
	}
}
