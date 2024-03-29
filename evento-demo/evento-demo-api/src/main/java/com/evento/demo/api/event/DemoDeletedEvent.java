package com.evento.demo.api.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class DemoDeletedEvent extends DomainEvent {

	private String demoId;

	public DemoDeletedEvent() {
	}

	public DemoDeletedEvent(String demoId) {
		this.demoId = demoId;
	}

	public String getDemoId() {
		return demoId;
	}

	public void setDemoId(String demoId) {
		this.demoId = demoId;
	}
}
