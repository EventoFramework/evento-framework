package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Command;

import java.util.Collection;

public interface HasCommandInvocations {
	void addCommandInvocation(Command command);
	Collection<Command> getCommandInvocations();
}
