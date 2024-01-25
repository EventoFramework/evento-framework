package org.evento.demo.api.command;

import org.evento.common.modeling.messaging.payload.ServiceCommand;

public class NotificationSendSilentCommand implements ServiceCommand {
	private String body;

	public NotificationSendSilentCommand(String body) {
		this.body = body;
	}

	public NotificationSendSilentCommand() {
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}
}
