package org.eventrails.common.modeling.messaging.message.application;

import org.eventrails.common.modeling.messaging.payload.ServiceCommand;

public class ServiceCommandMessage extends CommandMessage<ServiceCommand> {
	public ServiceCommandMessage(ServiceCommand command) {
		super(command);
	}

	public ServiceCommandMessage() {
	}
}
