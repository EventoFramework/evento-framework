package org.eventrails.modeling.messaging.message.bus;

public class ServiceHandleProjectorEventMessage extends InvocationMessage {
	private String eventName;

	private String projectorName;

	public ServiceHandleProjectorEventMessage(String eventName, String projectorName, String payload) {
		this.eventName = eventName;
		this.payload = payload;
		this.projectorName = projectorName;
	}

	public ServiceHandleProjectorEventMessage() {

	}

	public String getEventName() {
		return eventName;
	}

	public void setEventName(String eventName) {
		this.eventName = eventName;
	}

	public String getProjectorName() {
		return projectorName;
	}

	public void setProjectorName(String projectorName) {
		this.projectorName = projectorName;
	}
}
