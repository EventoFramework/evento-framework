package com.evento.common.modeling.exceptions;

/**
 * Exception thrown when an aggregate is not initialized.
 */
public class AggregateNotInitializedError extends RuntimeException {


	/**
	 * Constructs a new AggregateNotInitializedError with the specified detail message.
	 *
	 * @param message the detail message of the exception
	 */
	public AggregateNotInitializedError(String message) {
		super(message);
	}

	/**
	 * Builds an instance of AggregateNotInitializedError with the specified aggregateId.
	 *
	 * @param aggregateId the id of the aggregate
	 * @return an instance of AggregateNotInitializedError
	 */
	public static AggregateNotInitializedError build(String aggregateId) {
		return new AggregateNotInitializedError("The aggregate %s in not initialized".formatted(aggregateId));
	}
}
