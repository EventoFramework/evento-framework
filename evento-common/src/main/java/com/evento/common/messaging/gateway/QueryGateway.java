package com.evento.common.messaging.gateway;

import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.QueryResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The QueryGateway interface provides a way to send queries and receive the corresponding responses asynchronously.
 */
public interface QueryGateway extends Gateway {

	/**
	 * Queries the system with the given Query object and returns a CompletableFuture representing the response.
	 *
	 * @param query The Query object to be sent to the system.
	 * @param <T> The type of QueryResponse expected as the response.
	 * @return A CompletableFuture that resolves to the response of the query.
	 */
	default <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query) {
		return query(query, null);
	}

	/**
	 * Queries the system with the given Query object and returns a CompletableFuture representing the response.
	 *
	 * @param query The Query object to be sent to the system.
	 * @param <T> The type of QueryResponse expected as the response.
	 * @return A CompletableFuture that resolves to the response of the query.
	 */
	default <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, long timeout, TimeUnit unit) {
		return query(query, null, timeout, unit);
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
		return query(query, metadata, null, getDefaultTimeoutTime(), getDefaultTimeoutUnit());
	}

	/**
	 * Executes a query asynchronously and returns the corresponding response.
	 *
	 * @param query           The query object to be sent to the system.
	 * @param metadata        The metadata associated with the query.
	 * @param timeout       the maximum time to wait for the command execution to complete
	 * @param unit          the time unit of the timeout parameter
	 * @param <T>             The type parameter of the QueryResponse expected as the response.
	 * @return A CompletableFuture that resolves to the response of the query.
	 */
	default <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata, long timeout, TimeUnit unit) {
		return query(query, metadata, null, timeout, unit);
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
	default <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata, Message<?> handledMessage){
		return query(query, metadata, handledMessage, getDefaultTimeoutTime(), getDefaultTimeoutUnit());
	}

	/**
	 * Executes a query asynchronously and returns the corresponding response.
	 *
	 * @param query           The query object to be sent to the system.
	 * @param metadata        The metadata associated with the query.
	 * @param handledMessage  The handled message associated with the query.
	 * @param timeout       the maximum time to wait for the command execution to complete
	 * @param unit          the time unit of the timeout parameter
	 * @param <T>             The type parameter of the QueryResponse expected as the response.
	 * @return A CompletableFuture that resolves to the response of the query.
	 */
	<T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata, Message<?> handledMessage, long timeout, TimeUnit unit);
}
