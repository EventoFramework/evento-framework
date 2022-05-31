package org.eventrails.modeling.messaging;

import org.eventrails.modeling.messaging.payload.ServiceCommand;

public class ServiceCommandMessage extends CommandMessage<ServiceCommand> {
	public ServiceCommandMessage(ServiceCommand command) {
		super(command);
	}
}
