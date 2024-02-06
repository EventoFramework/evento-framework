package com.evento.common.modeling.messaging.payload;

import com.evento.common.modeling.messaging.query.QueryResponse;

import java.lang.reflect.ParameterizedType;


/**
 * The Query interface represents a query object that can be sent to a system to retrieve a response.
 * It extends the Payload interface.
 *
 * @param <T> The type of QueryResponse expected as the response.
 */
public interface Query<T extends QueryResponse<?>> extends Payload {

	/**
	 * Returns the response type of the Query.
	 *
	 * @return The Class object representing the response type.
	 */
	@SuppressWarnings("unchecked")
	public default Class<T> getResponseType() {
		return (Class<T>) ((ParameterizedType) ((ParameterizedType) getClass()
				.getGenericSuperclass()).getActualTypeArguments()[0]).getRawType();
	}
}
