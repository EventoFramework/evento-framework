package org.eventrails.common.modeling.messaging.message.application;

import org.eventrails.common.modeling.messaging.payload.Command;

public abstract class CommandMessage<T extends Command> extends Message<T> {

	public CommandMessage(T command) {
		super(command);
	}
	public CommandMessage() {}

	public String getCommandName() {
		return super.getPayloadName();
	}

}
