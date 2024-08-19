package com.evento.demo.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

import java.util.UUID;

public class NotificationSendSilentCommand extends ServiceCommand {
	private String body;

	private final String lockId = "NOTIFY_" + UUID.randomUUID();

	public NotificationSendSilentCommand(String body) {
		this.body = body;
	}

	public NotificationSendSilentCommand() {
	}

	@Override
	public String getLockId() {
		return lockId;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}
}
