package org.eventrails.demo.command.service;

import org.eventrails.common.utils.Inject;
import org.eventrails.demo.api.command.NotificationSendCommand;
import org.eventrails.demo.api.command.NotificationSendSilentCommand;
import org.eventrails.demo.api.event.NotificationSentEvent;
import org.eventrails.common.modeling.annotations.component.Service;
import org.eventrails.common.modeling.annotations.handler.CommandHandler;
import org.eventrails.common.messaging.gateway.CommandGateway;
import org.eventrails.common.messaging.gateway.QueryGateway;
import org.eventrails.common.modeling.messaging.message.application.CommandMessage;
import org.eventrails.demo.api.utils.Utils;
import org.eventrails.demo.external.ExternalNotificationService;

import java.util.UUID;

@Service
public class NotificationService {


	@Inject
	private ExternalNotificationService service;

	@CommandHandler
	NotificationSentEvent handle(NotificationSendCommand command,
								 CommandGateway commandGateway,
								 QueryGateway queryGateway,
								 CommandMessage commandMessage){
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
