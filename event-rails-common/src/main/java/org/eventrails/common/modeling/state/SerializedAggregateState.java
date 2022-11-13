package org.eventrails.common.modeling.state;

import org.eventrails.common.modeling.common.SerializedObject;

public class SerializedAggregateState<T extends AggregateState> extends SerializedObject<T> {
	public SerializedAggregateState(T object) {
		super(object);
	}

	public SerializedAggregateState() {
	}

	public T getAggregateState() {
		return getObject();
	}
}
