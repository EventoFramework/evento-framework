package com.evento.common.modeling.messaging.message.application;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/**
 * The DomainCommandMessage represents a message that carries a domain command payload.
 * It extends the CommandMessage class and is used to invoke command handler methods.
 * It contains the domain command payload and the ID of the aggregate the command is targeting.
 */
public class DomainCommandMessage extends CommandMessage<DomainCommand> {

	/**
	 * Constructs a new DomainCommandMessage with the given DomainCommand.
	 *
	 * @param command The DomainCommand to be carried by the message.
	 * @see DomainCommand
	 */
	public DomainCommandMessage(DomainCommand command) {
		super(command);
	}

	/**
	 * The DomainCommandMessage class represents a message that carries a domain command payload.
	 * It extends the CommandMessage class and is used to invoke command handler methods.
	 * It contains the domain command payload and the ID of the aggregate the command is targeting.
	 */
	public DomainCommandMessage() {
	}


}
