package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Command;

import java.util.Map;

public interface HasCommandInvocations {
	void addCommandInvocation(Command command, int line);

	Map<Integer, Command> getCommandInvocations();
}
