package org.eventrails.demo.command.service;

import org.eventrails.demo.api.command.NotificationSendCommand;
import org.eventrails.demo.api.command.NotificationSendSilentCommand;
import org.eventrails.demo.api.event.NotificationSentEvent;
import org.eventrails.common.modeling.annotations.component.Service;
import org.eventrails.common.modeling.annotations.handler.CommandHandler;
import org.eventrails.common.messaging.gateway.CommandGateway;
import org.eventrails.common.messaging.gateway.QueryGateway;
import org.eventrails.common.modeling.messaging.message.application.CommandMessage;

import java.util.UUID;

@Service
public class NotificationService {

	@CommandHandler
	NotificationSentEvent handle(NotificationSendCommand command,
								 CommandGateway commandGateway,
								 QueryGateway queryGateway,
								 CommandMessage commandMessage){
		System.out.println(this.getClass() + " - handle(NotificationSendCommand)");

		//Simulating external service sending notification and generating id
		String notificationId = UUID.randomUUID().toString();
		System.out.println(command.getBody());

		return new NotificationSentEvent(notificationId, command.getBody());
	}

	@CommandHandler
	void handle(NotificationSendSilentCommand command){

		System.out.println(this.getClass() + " - handle(NotificationSendSilentCommand)");
		//Simulating external service sending notification and generating id
		System.out.println(command.getBody());
	}
}
