package org.eventrails.demo.api.event;

import org.eventrails.modeling.messaging.payload.DomainEvent;

public class DemoCreatedEvent extends DomainEvent {

	private String demoId;
	private String name;
	private Long value;


	public DemoCreatedEvent() {
	}

	public DemoCreatedEvent(String demoId, String name, long value) {
		this.demoId = demoId;
		this.name = name;
		this.value = value;
	}

	public String getDemoId() {
		return demoId;
	}

	public void setDemoId(String demoId) {
		this.demoId = demoId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getValue() {
		return value;
	}

	public void setValue(long value) {
		this.value = value;
	}
}
