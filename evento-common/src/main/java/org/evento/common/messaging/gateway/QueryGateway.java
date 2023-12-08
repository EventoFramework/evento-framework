package org.evento.common.messaging.gateway;

import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.messaging.message.application.Metadata;
import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.QueryResponse;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

/**
 * The QueryGateway interface provides a way to send queries and receive the corresponding responses asynchronously.
 */
public interface QueryGateway {
	/**
	 * Queries the system with the given Query object and returns a CompletableFuture representing the response.
	 *
	 * @param query The Query object to be sent to the system.
	 * @param <T> The type of QueryResponse expected as the response.
	 * @return A CompletableFuture that resolves to the response of the query.
	 */
	@SuppressWarnings("unchecked")
	default <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query) {
		return query(query, null);
	}




	/**
	 * Executes a query asynchronously and returns the corresponding response.
	 *
	 * @param query           The query object to be sent to the system.
	 * @param metadata        The metadata associated with the query.
	 * @param <T>             The type parameter of the QueryResponse expected as the response.
	 * @return A CompletableFuture that resolves to the response of the query.
	 */
	default <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata) {
		return query(query, metadata, null);
	}

	/**
	 * Executes a query asynchronously and returns the corresponding response.
	 *
	 * @param query           The query object to be sent to the system.
	 * @param metadata        The metadata associated with the query.
	 * @param handledMessage  The handled message associated with the query.
	 * @param <T>             The type parameter of the QueryResponse expected as the response.
	 * @return A CompletableFuture that resolves to the response of the query.
	 */
	<T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata, Message<?> handledMessage);
}
