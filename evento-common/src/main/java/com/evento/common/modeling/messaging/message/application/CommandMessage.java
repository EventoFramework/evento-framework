package com.evento.common.modeling.messaging.message.application;

import com.evento.common.modeling.messaging.payload.Command;

/**
 * The CommandMessage class represents a message that carries a command payload.
 * It extends the Message class and is meant to be subclassed for specific types of commands.
 * <p>
 * CommandMessage objects can be used to send commands and invoke command handler methods.
 * They contain the command payload, metadata, timestamp, and other related information.
 * @param <T> the Command payload for this message
 */
public abstract class CommandMessage<T extends Command> extends Message<T> {

	private String aggregateId;
	private String lockId;

	/**
	 * CommandMessage is a subclass of Message that represents a message carrying a command payload.
	 * It is meant to be subclassed for specific types of commands.
	 *
     * @param command The command payload of this message
     */
	public CommandMessage(T command) {
		super(command);
		this.aggregateId = command.getAggregateId();
		this.lockId = command.getLockId();
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

	/**
	 * Retrieves the ID of the aggregate the command is targeting.
	 *
	 * @return The ID of the aggregate.
	 */
	public String getAggregateId() {
		return aggregateId;
	}

	/**
	 * Sets the ID of the aggregate that the command is targeting.
	 *
	 * @param aggregateId The ID of the aggregate as a string.
	 */
	public void setAggregateId(String aggregateId) {
		this.aggregateId = aggregateId;
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


	@Override
	public void setPayload(T payload) {
		super.setPayload(payload);
		this.lockId = payload.getLockId();
		this.aggregateId = payload.getAggregateId();
	}

	@Override
	public String toString() {
		return "CommandMessage{" +
				"aggregateId='" + aggregateId + '\'' +
				", lockId='" + lockId + '\'' +
				"} " + super.toString();
	}
}
