package org.evento.common.modeling.exceptions;

/**
 * RuntimeException indicating that an aggregate has already been initialized.
 */
public class AggregateInitializedError extends RuntimeException {


	public AggregateInitializedError() {
		super();
	}

	public AggregateInitializedError(String message) {
		super(message);
	}

	public static AggregateInitializedError build(String aggregateId) {
		return new AggregateInitializedError("The aggregate %s in initialized".formatted(aggregateId));
	}


}
