package org.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;

/**
 * Represents a message for invocation, with a payload that can be set and retrieved.
 */
public class InvocationMessage implements Serializable {
	protected String payload;

	/**
	 * Retrieves the payload of the invocation message.
	 *
	 * @return the payload of the invocation message
	 */
	public String getPayload() {
		return payload;
	}

	/**
	 * Sets the payload for the invocation message.
	 *
	 * @param payload the payload to set for the invocation message
	 */
	public void setPayload(String payload) {
		this.payload = payload;
	}
}
