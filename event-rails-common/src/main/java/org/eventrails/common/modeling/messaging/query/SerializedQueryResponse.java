package org.eventrails.common.modeling.messaging.query;

import org.eventrails.common.modeling.common.SerializedObject;

public class SerializedQueryResponse <T extends QueryResponse<?>> extends SerializedObject<T> {
	public SerializedQueryResponse(T object) {
		super(object);
	}

	public SerializedQueryResponse() {
	}
}
