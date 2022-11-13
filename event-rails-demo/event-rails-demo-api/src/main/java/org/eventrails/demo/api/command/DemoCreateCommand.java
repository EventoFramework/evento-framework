package org.eventrails.demo.api.command;


import org.eventrails.common.modeling.messaging.payload.DomainCommand;

public class DemoCreateCommand extends DomainCommand {

	private String demoId;
	private String name;
	private Long value;

	public DemoCreateCommand(String demoId, String name, long value) {
		this.demoId = demoId;
		this.name = name;
		this.value = value;
	}

	public DemoCreateCommand() {
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
