package org.evento.common.messaging.consumer;

import java.io.Serializable;

/**
 * Represents a response object containing the last sequence number for an event.
 */
public class EventLastSequenceNumberResponse implements Serializable {
	private long number;

	public EventLastSequenceNumberResponse() {
	}

	public EventLastSequenceNumberResponse(long number) {
		this.number = number;
	}

	public long getNumber() {
		return number;
	}

	public void setNumber(long number) {
		this.number = number;
	}
}
