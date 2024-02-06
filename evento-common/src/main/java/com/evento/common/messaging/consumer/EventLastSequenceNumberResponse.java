package com.evento.common.messaging.consumer;

import java.io.Serializable;

/**
 * The EventLastSequenceNumberResponse class represents a response object that contains the last sequence number for an event.
 */
public class EventLastSequenceNumberResponse implements Serializable {
	private long number;

	/**
	 * The EventLastSequenceNumberResponse class represents a response object that contains the last sequence number for an event.
	 */
	public EventLastSequenceNumberResponse() {
	}

	/**
	 * The EventLastSequenceNumberResponse class represents a response object that contains the last sequence number for an event.
     * @param number the sequence number
     */
	public EventLastSequenceNumberResponse(long number) {
		this.number = number;
	}

	/**
	 * Retrieves the number from the EventLastSequenceNumberResponse object. This method is used to get the last sequence number for an event.
	 *
	 * @return the last sequence number for the event
	 */
	public long getNumber() {
		return number;
	}

	/**
	 * Sets the number for the EventLastSequenceNumberResponse object. This method is used to set the last sequence number for an event.
	 *
	 * @param number the last sequence number for the event
	 */
	public void setNumber(long number) {
		this.number = number;
	}
}
