package org.evento.common.modeling.exceptions;

/**
 * Exception thrown when an attempt is made to interact with a deleted aggregate.
 */
public class AggregateDeletedError extends RuntimeException {

	/**
	 * Constructs a new {@code AggregateDeletedError} with the provided error message.
	 *
	 * @param message the error message
	 */
	public AggregateDeletedError(String message) {
		super(message);
	}

	/**
	 * Builds an {@code AggregateDeletedError} instance with the provided aggregate ID.
	 *
	 * @param aggregateId the ID of the deleted aggregate
	 * @return an {@code AggregateDeletedError} instance
	 */
	public static AggregateDeletedError build(String aggregateId) {
		return new AggregateDeletedError("The aggregate %s in deleted".formatted(aggregateId));
	}
}
