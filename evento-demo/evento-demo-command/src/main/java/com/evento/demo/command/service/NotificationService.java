package com.evento.demo.command.service;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.common.modeling.messaging.message.application.CommandMessage;
import com.evento.demo.api.command.NotificationSendCommand;
import com.evento.demo.api.command.NotificationSendSilentCommand;
import com.evento.demo.api.event.NotificationSentEvent;
import com.evento.demo.api.utils.Utils;
import com.evento.demo.external.ExternalNotificationService;

@Service
public class NotificationService {

	private final ExternalNotificationService service;

	public NotificationService(ExternalNotificationService service) {
		this.service = service;
	}

	@CommandHandler
	NotificationSentEvent handle(NotificationSendCommand command,
								 CommandGateway commandGateway,
								 CommandMessage<NotificationSendCommand> commandMessage) {
		if(command.getBody() == null){
			throw new RuntimeException("error.body.null");
		}
		Utils.logMethodFlow(this, "handle", command, "BEGIN");
		String notificationId = service.send(command.getBody());
		Utils.logMethodFlow(this, "handle", command, "END");
		return new NotificationSentEvent(notificationId, command.getBody());
	}

	@CommandHandler
	void handle(NotificationSendSilentCommand command) {
		Utils.logMethodFlow(this, "handle", command, "BEGIN");
		service.send(command.getBody());
		Utils.logMethodFlow(this, "handle", command, "END");
	}
}
