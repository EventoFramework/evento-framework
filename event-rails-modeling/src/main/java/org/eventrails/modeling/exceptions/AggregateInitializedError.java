package org.eventrails.modeling.exceptions;

public class AggregateInitializedError extends RuntimeException {


	public AggregateInitializedError() {
		super();
	}

	public AggregateInitializedError(String message) {
		super(message);
	}

	public static AggregateInitializedError build(String aggregateId){
		return new AggregateInitializedError("The aggregate %s in initialized".formatted(aggregateId));
	}


}
