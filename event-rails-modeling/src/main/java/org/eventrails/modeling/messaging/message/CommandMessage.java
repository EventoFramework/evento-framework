package org.eventrails.modeling.messaging.message;

import org.eventrails.modeling.messaging.payload.Command;

public abstract class CommandMessage<T extends Command> extends Message<T> {

	public CommandMessage(T command) {
		super(command);
	}
	public CommandMessage() {}

	public String getCommandName() {
		return super.getPayloadName();
	}

}
