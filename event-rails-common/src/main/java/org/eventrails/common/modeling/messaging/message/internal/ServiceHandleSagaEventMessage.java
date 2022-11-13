package org.eventrails.common.modeling.messaging.message.internal;

public class ServiceHandleSagaEventMessage extends InvocationMessage {
	private String eventName;

	private String sagaName;

	public ServiceHandleSagaEventMessage(String eventName, String sagaName, String payload) {
		this.eventName = eventName;
		this.payload = payload;
		this.sagaName = sagaName;
	}

	public ServiceHandleSagaEventMessage() {

	}

	public String getEventName() {
		return eventName;
	}

	public void setEventName(String eventName) {
		this.eventName = eventName;
	}

	public String getSagaName() {
		return sagaName;
	}

	public void setSagaName(String sagaName) {
		this.sagaName = sagaName;
	}
}
