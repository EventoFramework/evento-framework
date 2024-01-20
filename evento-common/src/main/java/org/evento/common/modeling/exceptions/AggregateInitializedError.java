package org.evento.common.modeling.exceptions;

/**
 * RuntimeException indicating that an aggregate has already been initialized.
 */
public class AggregateInitializedError extends RuntimeException {


	/**
	 * RuntimeException indicating that an aggregate has already been initialized.
	 *
	 * @param message the detailed error message
	 */
	public AggregateInitializedError(String message) {
		super(message);
	}

	/**
	 * Builds an instance of AggregateInitializedError.
	 *
	 * @param aggregateId The ID of the aggregate.
	 * @return The AggregateInitializedError instance.
	 */
	public static AggregateInitializedError build(String aggregateId) {
		return new AggregateInitializedError("The aggregate %s in initialized".formatted(aggregateId));
	}


}
