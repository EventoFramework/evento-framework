package com.evento.common.modeling.messaging.message.application;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

/**
 * The ServiceCommandMessage class represents a message that carries a service command payload.
 * It extends the CommandMessage class and is used to send service commands and invoke service command handler methods.
 * <p>
 * ServiceCommandMessage objects contain the command payload, metadata, timestamp, and other related information.
 */
public class ServiceCommandMessage extends CommandMessage<ServiceCommand> {

	private String lockId;
	/**
	 * Initializes a new instance of the ServiceCommandMessage class with the given ServiceCommand.
	 *
	 * @param command The ServiceCommand object representing the command payload.
	 * @see ServiceCommand
	 */
	public ServiceCommandMessage(ServiceCommand command) {
		super(command);
		lockId = command.getLockId();
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

	/**
	 * Sets the lock ID for the ServiceCommandMessage.
	 *
	 * @param lockId The lock ID to be set.
     */
	public void setLockId(String lockId) {
		this.lockId = lockId;
	}

	/**
	 * Retrieves the lock ID associated with the ServiceCommandMessage.
	 *
	 * @return The lock ID associated with the ServiceCommandMessage.
     */
	public String getLockId() {
		return lockId;
	}
}
