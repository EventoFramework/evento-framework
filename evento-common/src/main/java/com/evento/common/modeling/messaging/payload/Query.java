package com.evento.common.modeling.messaging.payload;

import com.evento.common.modeling.messaging.query.QueryResponse;

import java.lang.reflect.ParameterizedType;


/**
 * The Query class is an abstract class that represents a query object. It extends the TrackablePayload class.
 * It is generic, with a type parameter T that must extend the QueryResponse class.
 *
 * @param <T> The type of the response object that the Query returns.
 *
 * @see TrackablePayload
 * @see QueryResponse
 */
public abstract class Query<T extends QueryResponse<?>> extends TrackablePayload {

	/**
	 * Returns the response type of the Query.
	 *
	 * @return The Class object representing the response type.
	 */
	@SuppressWarnings("unchecked")
	public Class<T> getResponseType() {
		return (Class<T>) ((ParameterizedType) ((ParameterizedType) getClass()
				.getGenericSuperclass()).getActualTypeArguments()[0]).getRawType();
	}
}
