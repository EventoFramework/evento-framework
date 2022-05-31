package org.eventrails.demo.command.service;

import org.eventrails.demo.api.command.NotificationSendCommand;
import org.eventrails.demo.api.command.NotificationSendSilentCommand;
import org.eventrails.demo.api.event.NotificationSentEvent;
import org.eventrails.modeling.annotations.component.Service;
import org.eventrails.modeling.annotations.handler.CommandHandler;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.gateway.QueryGateway;
import org.eventrails.modeling.messaging.CommandMessage;

import java.util.UUID;

@Service
public class NotificationService {

	@CommandHandler
	NotificationSentEvent handle(NotificationSendCommand command,
								 CommandGateway commandGateway,
								 QueryGateway queryGateway,
								 CommandMessage commandMessage){

		//Simulating external service sending notification and generating id
		String notificationId = UUID.randomUUID().toString();
		System.out.println(command.getBody());

		return new NotificationSentEvent(notificationId, command.getBody());
	}

	@CommandHandler
	void handle(NotificationSendSilentCommand command){

		//Simulating external service sending notification and generating id
		System.out.println(command.getBody());
	}
}
