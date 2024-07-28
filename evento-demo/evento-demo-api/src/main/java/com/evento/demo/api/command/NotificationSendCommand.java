package com.evento.demo.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

import java.util.UUID;

public class NotificationSendCommand extends ServiceCommand {
	private String body;
	private String lockId = "NOTIFY_" + UUID.randomUUID();


	public NotificationSendCommand(String body) {
		this.body = body;
	}

	public NotificationSendCommand() {
	}

	@Override
	public String getAggregateId() {
		return null;
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
