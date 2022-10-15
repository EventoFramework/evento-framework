package org.eventrails.modeling.messaging.query;

import org.eventrails.modeling.common.SerializedObject;
import org.eventrails.modeling.state.AggregateState;

public class SerializedQueryResponse <T extends QueryResponse<?>> extends SerializedObject<T> {
	public SerializedQueryResponse(T object) {
		super(object);
	}

	public SerializedQueryResponse() {
	}
}
