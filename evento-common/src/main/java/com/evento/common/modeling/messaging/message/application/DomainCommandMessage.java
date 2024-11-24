package com.evento.common.modeling.messaging.message.application;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/**
 * The DomainCommandMessage represents a message that carries a domain command payload.
 * It extends the CommandMessage class and is used to invoke command handler methods.
 * It contains the domain command payload and the ID of the aggregate the command is targeting.
 */
public class DomainCommandMessage extends CommandMessage<DomainCommand> {

	private boolean invalidateAggregateCaches = false;
	private boolean invalidateAggregateSnapshot = false;

	/**
	 * Constructs a new DomainCommandMessage with the given DomainCommand.
	 *
	 * @param command The DomainCommand to be carried by the message.
	 * @see DomainCommand
	 */
	public DomainCommandMessage(DomainCommand command) {
		super(command);
		setInvalidateAggregateSnapshot(command.isInvalidateAggregateSnapshot());
		setInvalidateAggregateCaches(command.isInvalidateAggregateCaches());
	}

	/**
	 * The DomainCommandMessage class represents a message that carries a domain command payload.
	 * It extends the CommandMessage class and is used to invoke command handler methods.
	 * It contains the domain command payload and the ID of the aggregate the command is targeting.
	 */
	public DomainCommandMessage() {
	}


	/**
	 * Determines whether to invalidate the cached data of an aggregate.
	 *
	 * @return True if the cached data of the aggregate should be invalidated, False otherwise.
	 */
	public boolean isInvalidateAggregateCaches() {
		return invalidateAggregateCaches;
	}

	/**
	 * Determines whether to invalidate the cached data of an aggregate.
	 */
	public void setInvalidateAggregateCaches(boolean invalidateAggregateCaches) {
		this.invalidateAggregateCaches = invalidateAggregateCaches;
	}

	/**
	 * Determines whether to invalidate the snapshot data of an aggregate.
	 *
	 * @return True if the snapshot data of the aggregate should be invalidated, False otherwise.
	 */
	public boolean isInvalidateAggregateSnapshot() {
		return invalidateAggregateSnapshot;
	}

	/**
	 * Sets whether to invalidate the snapshot data of an aggregate.
	 *
	 * @param invalidateAggregateSnapshot True if the snapshot data of the aggregate should be invalidated, False otherwise.
	 */
	public void setInvalidateAggregateSnapshot(boolean invalidateAggregateSnapshot) {
		this.invalidateAggregateSnapshot = invalidateAggregateSnapshot;
	}


	@Override
	public String toString() {
		return "DomainCommandMessage{" +
				"invalidateAggregateCaches=" + invalidateAggregateCaches +
				", invalidateAggregateSnapshot=" + invalidateAggregateSnapshot +
				"} " + super.toString();
	}
}
