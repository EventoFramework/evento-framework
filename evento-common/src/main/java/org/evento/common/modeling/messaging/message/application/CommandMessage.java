package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.messaging.payload.Command;

/**
 * The CommandMessage class represents a message that carries a command payload.
 * It extends the Message class and is meant to be subclassed for specific types of commands.
 * <p>
 * CommandMessage objects can be used to send commands and invoke command handler methods.
 * They contain the command payload, metadata, timestamp, and other related information.
 */
public abstract class CommandMessage<T extends Command> extends Message<T> {

	/**
	 * CommandMessage is a subclass of Message that represents a message carrying a command payload.
	 * It is meant to be subclassed for specific types of commands.
	 *
     * @param command The command paylod of this message
     */
	public CommandMessage(T command) {
		super(command);
	}

	/**
	 * The CommandMessage class represents a message that carries a command payload.
	 * It extends the Message class and is meant to be subclassed for specific types of commands.
	 * <p>
	 * CommandMessage objects can be used to send commands and invoke command handler methods.
	 * They contain the command payload, metadata, timestamp, and other related information.
	 */
	public CommandMessage() {
	}

	/**
	 * Returns the name of the command.
	 *
	 * @return The name of the command.
	 */
	public String getCommandName() {
		return super.getPayloadName();
	}
}
