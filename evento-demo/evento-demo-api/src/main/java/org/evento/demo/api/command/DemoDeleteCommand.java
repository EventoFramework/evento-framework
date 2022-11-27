package org.evento.demo.api.command;

import org.evento.common.modeling.messaging.payload.DomainCommand;

public class DemoDeleteCommand extends DomainCommand {

	private String demoId;

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

	public DemoDeleteCommand() {
	}

	public DemoDeleteCommand(String demoId) {
		this.demoId = demoId;
	}
}
