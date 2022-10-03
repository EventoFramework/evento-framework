package org.eventrails.modeling.messaging.invocation;

import org.eventrails.modeling.messaging.message.DomainCommandMessage;
import org.eventrails.modeling.messaging.message.DomainEventMessage;
import org.eventrails.modeling.messaging.message.ServiceCommandMessage;
import org.eventrails.modeling.state.AggregateState;

import java.util.Collection;

public class ServiceCommandHandlerInvocation {
	private ServiceCommandMessage commandMessage;

	public ServiceCommandHandlerInvocation(ServiceCommandMessage commandMessage) {
		this.commandMessage = commandMessage;
	}

	public ServiceCommandHandlerInvocation() {
	}

	public ServiceCommandMessage getCommandMessage() {
		return commandMessage;
	}

	public void setCommandMessage(ServiceCommandMessage commandMessage) {
		this.commandMessage = commandMessage;
	}
}
