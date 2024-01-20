package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Command;

import java.util.Map;

/**
 * The HasCommandInvocations interface represents an object that can keep track of command invocations.
 * It provides methods to add and retrieve command invocations.
 */
public interface HasCommandInvocations {
	/**
	 * Adds a command invocation to the object that keeps track of command invocations.
	 *
	 * @param command The command object to be added.
	 * @param line    The line number where the command invocation occurs.
	 */
	void addCommandInvocation(Command command, int line);

	/**
	 * Gets a map of command invocations.
	 *
	 * @return A map of command invocations, where the key is the line number and the value is the Command object.
	 */
	Map<Integer, Command> getCommandInvocations();
}
