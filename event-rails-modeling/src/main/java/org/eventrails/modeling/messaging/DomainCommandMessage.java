package org.eventrails.modeling.messaging;

import org.eventrails.modeling.messaging.payload.DomainCommand;

public class DomainCommandMessage extends CommandMessage<DomainCommand> {
	public DomainCommandMessage(DomainCommand command) {
		super(command);
	}
}
