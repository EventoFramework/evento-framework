package org.evento.common.modeling.exceptions;

/**
 * Exception thrown when an attempt is made to interact with a deleted aggregate.
 */
public class AggregateDeletedError extends RuntimeException {

	/**
	 * Exception thrown when an attempt is made to interact with a deleted aggregate.
	 */
	public AggregateDeletedError(String message) {
		super(message);
	}

	public static AggregateDeletedError build(String aggregateId) {
		return new AggregateDeletedError("The aggregate %s in deleted".formatted(aggregateId));
	}
}
