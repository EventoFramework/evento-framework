package org.evento.common.messaging.consumer;

import java.io.Serializable;

/**
 * The EventFetchRequest class represents a request to fetch events from a context.
 * It contains the context, the last sequence number, the limit, and the component name.
 */
public class EventFetchRequest implements Serializable {

	private String context;
	private long lastSequenceNumber;
	private int limit;

	private String componentName;

	/**
	 *
	 */
	public EventFetchRequest() {
	}

	/**
	 * The EventFetchRequest class represents a request to fetch events from a context.
	 * It contains the context, the last sequence number, the limit, and the component name.
     * @param context context from to filler events
     * @param lastSequenceNumber to fetch only later events
     * @param limit events to fetch
     * @param componentName caller component to fetch only required events
     */
	public EventFetchRequest(String context, long lastSequenceNumber, int limit, String componentName) {
		this.context = context;
		this.lastSequenceNumber = lastSequenceNumber;
		this.limit = limit;
		this.componentName = componentName;
	}

	/**
	 * Retrieves the last sequence number from the EventFetchRequest object.
	 * This sequence number represents the position of the last event that was fetched from the context.
	 *
	 * @return the last sequence number
	 */
	public long getLastSequenceNumber() {
		return lastSequenceNumber;
	}

	/**
	 * Sets the last sequence number for the EventFetchRequest object.
	 * This sequence number represents the position of the last event that was fetched from the context.
	 *
	 * @param lastSequenceNumber the last sequence number to set
	 */
	public void setLastSequenceNumber(long lastSequenceNumber) {
		this.lastSequenceNumber = lastSequenceNumber;
	}

	/**
	 * Retrieves the limit value from the EventFetchRequest object.
	 *
	 * @return the limit value
	 */
	public int getLimit() {
		return limit;
	}

	/**
	 * Sets the limit value for the EventFetchRequest object.
	 * The limit value represents the maximum number of events to fetch from the context.
	 *
	 * @param limit the limit value to set
	 */
	public void setLimit(int limit) {
		this.limit = limit;
	}

	/**
	 * Retrieves the component name associated with the EventFetchRequest object.
	 *
	 * @return the component name
	 */
	public String getComponentName() {
		return componentName;
	}

	/**
	 * Sets the component name for the EventFetchRequest object.
	 * The component name represents the identifier of the component associated with the request.
	 *
	 * @param componentName the component name to set
	 */
	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}

	/**
	 * Retrieves the context from the EventFetchRequest object.
	 * The context represents the identifier of the context associated with the request.
	 *
	 * @return the context
	 */
	public String getContext() {
		return context;
	}

	/**
	 * Sets the context for the EventFetchRequest object.
	 * <p>
	 * This method allows you to set the context, which represents the identifier of the context associated with the request.
	 *
	 * @param context the context to set
	 */
	public void setContext(String context) {
		this.context = context;
	}
}
