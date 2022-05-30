package org.eventrails.modeling.messaging;

import org.eventrails.modeling.messaging.payload.Command;

public abstract class CommandMessage<T extends Command> extends Message<T> {
	public CommandMessage(T command) {
		super(command);
	}
}
