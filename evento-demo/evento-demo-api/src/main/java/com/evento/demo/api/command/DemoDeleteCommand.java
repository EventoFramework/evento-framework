package com.evento.demo.api.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class DemoDeleteCommand extends DomainCommand {

	private String demoId;

	public DemoDeleteCommand() {
	}

	public DemoDeleteCommand(String demoId) {
		this.demoId = demoId;
	}

	public String getDemoId() {
		return demoId;
	}

	public void setDemoId(String demoId) {
		this.demoId = demoId;
	}

	@Override
	public String getAggregateId() {
		return demoId;
	}
}
