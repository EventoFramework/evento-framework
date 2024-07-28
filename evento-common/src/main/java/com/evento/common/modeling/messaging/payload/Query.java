package com.evento.common.modeling.messaging.payload;

import com.evento.common.modeling.messaging.query.QueryResponse;

import java.lang.reflect.ParameterizedType;


/**
 * The Query abstract class represents a query object that can be sent to a system to retrieve a response.
 * It extends the Payload interface.
 *
 * @param <T> The type of QueryResponse expected as the response.
 */
public abstract class Query<T extends QueryResponse<?>> extends PayloadWithContext {

	private String aggregateId = null;

	@Override
	public String getAggregateId() {
		return aggregateId;
	}

	/**
	 * Sets the aggregate ID for the query.
	 * This method allows you to set the aggregate ID for a query object.
	 *
	 * @param aggregateId the aggregate ID to be set
	 * @param <T> the type of the query
	 * @return the query itself with the aggregate ID set
	 */
	@SuppressWarnings("unchecked")
	public <T extends Query<?>> T setAggregateId(String aggregateId) {
		this.aggregateId = aggregateId;
		return (T) this;
	}

	/**
	 * Sets the aggregate ID for the query.
	 * This method allows you to set the aggregate ID for a query object.
	 *
	 * @param payload the payload containing the aggregate ID to be set
	 * @param <T> the type of the query
	 * @return the query itself with the aggregate ID set
	 */
	@SuppressWarnings("unchecked")
	public <T extends Query<?>> T setAggregateId(PayloadWithContext payload) {
		this.aggregateId = payload.getAggregateId();
		return (T) this;
	}


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
