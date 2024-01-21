package org.evento.common.modeling.state;

import org.evento.common.modeling.common.SerializedObject;

/**
 * Represents a serialized aggregate state that can be converted to and from a string representation.
 * The serialized aggregate state is a subclass of the SerializedObject class.
 *
 * @param <T> the type of the serialized aggregate state, which must extend the AggregateState class
 */
public class SerializedAggregateState<T extends AggregateState> extends SerializedObject<T> {
	/**
	 * Creates a new SerializedAggregateState object with the specified object.
	 *
	 * @param object the object to be serialized
     */
	public SerializedAggregateState(T object) {
		super(object);
	}

	/**
	 * Represents a serialized aggregate state that can be converted to and from a string representation.
	 * The serialized aggregate state is a subclass of the SerializedObject class.
	 *
     */
	public SerializedAggregateState() {
	}

	/**
	 * Retrieves the aggregate state from the serialized aggregate state.
	 *
	 * @return the aggregate state
	 */
	public T getAggregateState() {
		return getObject();
	}
}
