package org.eventrails.parser.model.handler;

import org.eventrails.parser.model.payload.Command;

import java.util.Collection;

public interface HasCommandInvocations {
	void addCommandInvocation(Command command);
	Collection<Command> getCommandInvocations();
}
