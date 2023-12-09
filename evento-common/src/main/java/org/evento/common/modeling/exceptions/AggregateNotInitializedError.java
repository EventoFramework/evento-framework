package org.evento.common.modeling.exceptions;

/**
 * Exception thrown when an aggregate is not initialized.
 */
public class AggregateNotInitializedError extends RuntimeException {


	public AggregateNotInitializedError() {
		super();
	}

	public AggregateNotInitializedError(String message) {
		super(message);
	}

	public static AggregateNotInitializedError build(String aggregateId) {
		return new AggregateNotInitializedError("The aggregate %s in not initialized".formatted(aggregateId));
	}
}
