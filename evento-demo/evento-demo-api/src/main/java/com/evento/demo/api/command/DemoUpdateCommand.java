package com.evento.demo.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class DemoUpdateCommand implements DomainCommand {

	private String demoId;
	private String name;
	private Long value;

	public DemoUpdateCommand(String demoId, String name, long value) {
		this.demoId = demoId;
		this.name = name;
		this.value = value;
	}

	public DemoUpdateCommand() {
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

	@Override
	public String getAggregateId() {
		return demoId;
	}
}
