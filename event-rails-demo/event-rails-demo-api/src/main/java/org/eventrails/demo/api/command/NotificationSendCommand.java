package org.eventrails.demo.api.command;

import org.eventrails.modeling.messaging.payload.ServiceCommand;

public class NotificationSendCommand extends ServiceCommand {
	private String body;

	public NotificationSendCommand(String body) {
		this.body = body;
	}

	public NotificationSendCommand() {
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	@Override
	public String getLockId() {
		return null;
	}
}
