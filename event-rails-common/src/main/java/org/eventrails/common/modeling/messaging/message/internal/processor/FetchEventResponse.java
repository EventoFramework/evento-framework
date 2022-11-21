package org.eventrails.common.modeling.messaging.message.internal.processor;

import java.io.Serializable;

public class FetchEventResponse implements Serializable {

	private boolean done;
	private long count;

	public FetchEventResponse() {
	}

	public FetchEventResponse(boolean done, long count) {
		this.done = done;
		this.count = count;
	}

	public boolean getDone() {
		return done;
	}

	public void setDone(boolean done) {
		this.done = done;
	}

	public long getCount() {
		return count;
	}

	public void setCount(long count) {
		this.count = count;
	}

	@Override
	public String toString() {
		return "FetchEventResponse{" +
				"done=" + done +
				", count=" + count +
				'}';
	}
}
