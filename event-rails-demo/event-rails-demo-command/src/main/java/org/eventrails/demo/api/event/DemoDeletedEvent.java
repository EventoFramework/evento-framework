package org.eventrails.demo.api.event;

import org.eventrails.modeling.messaging.payload.DomainEvent;

public class DemoDeletedEvent extends DomainEvent {

	private String demoId;

	public DemoDeletedEvent(String demoId) {
		this.demoId = demoId;
	}

	public DemoDeletedEvent() {
	}

	public String getDemoId() {
		return demoId;
	}

	public void setDemoId(String demoId) {
		this.demoId = demoId;
	}
}
