package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.messaging.payload.DomainCommand;

/**
 * The DomainCommandMessage represents a message that carries a domain command payload.
 * It extends the CommandMessage class and is used to invoke command handler methods.
 * It contains the domain command payload and the ID of the aggregate the command is targeting.
 */
public class DomainCommandMessage extends CommandMessage<DomainCommand> {
	private String aggregateId;

	/**
	 * Constructs a new DomainCommandMessage with the given DomainCommand.
	 *
	 * @param command The DomainCommand to be carried by the message.
	 * @see DomainCommand
	 */
	public DomainCommandMessage(DomainCommand command) {
		super(command);
		this.aggregateId = command.getAggregateId();
	}

	public DomainCommandMessage() {
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
	 * Sets the payload of the DomainCommandMessage.
	 *
	 * @param payload The DomainCommand payload to be set.
	 */
	@Override
	public void setPayload(DomainCommand payload) {
		super.setPayload(payload);
		this.aggregateId = payload.getAggregateId();
	}
}
