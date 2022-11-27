package org.evento.common.modeling.state;

import org.evento.common.modeling.common.SerializedObject;

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
