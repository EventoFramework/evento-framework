package org.evento.common.modeling.exceptions;

public class AggregateDeletedError extends RuntimeException {

	public AggregateDeletedError() {
		super();
	}

	public AggregateDeletedError(String message) {
		super(message);
	}

	public static AggregateDeletedError build(String aggregateId) {
		return new AggregateDeletedError("The aggregate %s in deleted".formatted(aggregateId));
	}
}
