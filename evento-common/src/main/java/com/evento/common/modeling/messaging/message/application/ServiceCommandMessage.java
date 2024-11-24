package com.evento.common.modeling.messaging.message.application;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

/**
 * The ServiceCommandMessage class represents a message that carries a service command payload.
 * It extends the CommandMessage class and is used to send service commands and invoke service command handler methods.
 * <p>
 * ServiceCommandMessage objects contain the command payload, metadata, timestamp, and other related information.
 */
public class ServiceCommandMessage extends CommandMessage<ServiceCommand> {

	/**
	 * Initializes a new instance of the ServiceCommandMessage class with the given ServiceCommand.
	 *
	 * @param command The ServiceCommand object representing the command payload.
	 * @see ServiceCommand
	 */
	public ServiceCommandMessage(ServiceCommand command) {
		super(command);
	}

	/**
	 * The ServiceCommandMessage class represents a message that carries a service command payload.
	 * It extends the CommandMessage class and is used to send service commands and invoke service command handler methods.
	 * <p>
	 * ServiceCommandMessage objects contain the command payload, metadata, timestamp, and other related information.
	 *
	 * @see CommandMessage
	 */
	public ServiceCommandMessage() {
	}

	@Override
	public String toString() {
		return "ServiceCommandMessage{} " + super.toString();
	}
}
