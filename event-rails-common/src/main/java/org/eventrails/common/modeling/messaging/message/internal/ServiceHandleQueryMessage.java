package org.eventrails.common.modeling.messaging.message.internal;

public class ServiceHandleQueryMessage extends InvocationMessage {
	private String queryName;

	public ServiceHandleQueryMessage(String queryName, String payload) {
		this.queryName = queryName;
		this.payload = payload;
	}

	public ServiceHandleQueryMessage() {

	}

	public String getQueryName() {
		return queryName;
	}

	public void setQueryName(String queryName) {
		this.queryName = queryName;
	}
}