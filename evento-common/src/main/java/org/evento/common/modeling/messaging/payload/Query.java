package org.evento.common.modeling.messaging.payload;

import org.evento.common.modeling.messaging.query.QueryResponse;

import java.lang.reflect.ParameterizedType;

public abstract class Query<T extends QueryResponse<?>> extends Payload {

	@SuppressWarnings("unchecked")
	public Class<T> getResponseType() {
		return (Class<T>) ((ParameterizedType) ((ParameterizedType) getClass()
				.getGenericSuperclass()).getActualTypeArguments()[0]).getRawType();
	}
}
