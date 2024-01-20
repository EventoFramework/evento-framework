package org.evento.common.messaging.consumer;

import java.io.Serializable;

/**
 * The EventFetchRequest class represents a request to fetch events from a specific context,
 * starting from a given sequence number, with a specified limit, and targeting a specific component.
 * <p>
 * The class provides methods to get and set the context, last sequence number, limit, and component name.
 */
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
