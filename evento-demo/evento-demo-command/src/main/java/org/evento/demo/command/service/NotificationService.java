package org.evento.demo.command.service;

import org.evento.common.utils.Inject;
import org.evento.demo.api.command.NotificationSendCommand;
import org.evento.demo.api.command.NotificationSendSilentCommand;
import org.evento.demo.api.event.NotificationSentEvent;
import org.evento.common.modeling.annotations.component.Service;
import org.evento.common.modeling.annotations.handler.CommandHandler;
import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.QueryGateway;
import org.evento.common.modeling.messaging.message.application.CommandMessage;
import org.evento.demo.api.utils.Utils;
import org.evento.demo.external.ExternalNotificationService;

@Service
public class NotificationService {

	@Inject
	private ExternalNotificationService service;

	@CommandHandler
	NotificationSentEvent handle(NotificationSendCommand command,
								 CommandGateway commandGateway,
								 CommandMessage<NotificationSendCommand> commandMessage){
		Utils.logMethodFlow(this,"handle", command, "BEGIN");
		String notificationId = service.send(command.getBody());
		Utils.logMethodFlow(this,"handle", command, "END");
		return new NotificationSentEvent(notificationId, command.getBody());
	}

	@CommandHandler
	void handle(NotificationSendSilentCommand command){
		Utils.logMethodFlow(this,"handle", command, "BEGIN");
		service.send(command.getBody());
		Utils.logMethodFlow(this,"handle", command, "END");
	}
}
