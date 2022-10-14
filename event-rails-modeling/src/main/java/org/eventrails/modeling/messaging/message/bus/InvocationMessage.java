package org.eventrails.modeling.messaging.message.bus;

import java.io.Serializable;

public class InvocationMessage implements Serializable {
	protected String payload;

	public String getPayload() {
		return payload;
	}

	public void setPayload(String payload) {
		this.payload = payload;
	}
}
