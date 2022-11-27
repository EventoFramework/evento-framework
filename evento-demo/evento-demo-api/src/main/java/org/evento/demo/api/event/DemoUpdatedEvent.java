package org.evento.demo.api.event;

import org.evento.common.modeling.messaging.payload.DomainEvent;

public class DemoUpdatedEvent extends DomainEvent {

	private String demoId;
	private String name;
	private Long value;

	public DemoUpdatedEvent() {
	}

	public DemoUpdatedEvent(String demoId, String name, long value) {
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
