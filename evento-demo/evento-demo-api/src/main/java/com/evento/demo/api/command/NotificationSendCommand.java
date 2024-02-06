package com.evento.demo.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class NotificationSendCommand implements ServiceCommand {
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
}
