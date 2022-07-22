package org.eventrails.demo.api.command;

import org.eventrails.modeling.messaging.payload.DomainCommand;

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
}
