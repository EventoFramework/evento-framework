package org.evento.common.modeling.messaging.payload;

import org.evento.common.modeling.messaging.query.QueryResponse;

import java.lang.reflect.ParameterizedType;

/**
 * The Query class is an abstract class that represents a query object.
 * It extends the Payload class.
 *
 * @param <T> The type of QueryResponse that the Query class will return.
 */
public abstract class Query<T extends QueryResponse<?>> extends Payload {

	/**
	 * Returns the response type of the Query.
	 *
	 * @param <T> The type parameter that the QueryResponse will return.
	 * @return The Class object representing the response type.
	 */
	@SuppressWarnings("unchecked")
	public Class<T> getResponseType() {
		return (Class<T>) ((ParameterizedType) ((ParameterizedType) getClass()
				.getGenericSuperclass()).getActualTypeArguments()[0]).getRawType();
	}
}
