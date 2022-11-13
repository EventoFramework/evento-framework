package org.eventrails.common.modeling.messaging.message.internal;

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
