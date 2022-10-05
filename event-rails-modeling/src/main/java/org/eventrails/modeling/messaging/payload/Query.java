package org.eventrails.modeling.messaging.payload;

import org.eventrails.modeling.messaging.query.QueryResponse;

import java.lang.reflect.ParameterizedType;

public abstract class Query<T extends QueryResponse<?>> extends Payload {


	public Class<T> getResponseType() {
		return (Class<T>) ((ParameterizedType)((ParameterizedType) getClass()
				.getGenericSuperclass()).getActualTypeArguments()[0]).getRawType();
	}
}
