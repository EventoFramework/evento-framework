package org.evento.common.messaging.consumer;

import java.io.Serializable;

public class EventFetchRequest implements Serializable {

	private String context;
	private long lastSequenceNumber;
	private int limit;

	private String componentName;

	public EventFetchRequest() {
	}

	public EventFetchRequest(String context, long lastSequenceNumber, int limit, String componentName) {
		this.context = context;
		this.lastSequenceNumber = lastSequenceNumber;
		this.limit = limit;
		this.componentName = componentName;
	}

	public long getLastSequenceNumber() {
		return lastSequenceNumber;
	}

	public void setLastSequenceNumber(long lastSequenceNumber) {
		this.lastSequenceNumber = lastSequenceNumber;
	}

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public String getComponentName() {
		return componentName;
	}

	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}

	public String getContext() {
		return context;
	}

	public void setContext(String context) {
		this.context = context;
	}
}
