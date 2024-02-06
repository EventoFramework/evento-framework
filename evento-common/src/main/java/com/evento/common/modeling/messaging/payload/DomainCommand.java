package com.evento.common.modeling.messaging.payload;

/**
 * The DomainCommand class is an abstract class that represents a command related to a domain object.
 * It extends the Command class.
 * Subclasses of DomainCommand must implement the getAggregateId() method to provide the ID of the aggregate the command is targeting.
 */
public interface DomainCommand extends Command {
	/**
	 * Retrieves the ID of the aggregate that the command is targeting.
	 *
	 * @return The aggregate ID as a string.
	 */
	String getAggregateId();
}
