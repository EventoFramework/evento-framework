package com.evento.common.messaging.gateway;

import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.payload.View;
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

	/**
	 * Executes a query with {@link QueryOptions}: timeout, metadata, and the writes the
	 * answer must reflect (read-your-writes). Named apart from the {@code query} overloads
	 * so that existing calls such as {@code query(q, null)} stay unambiguous.
	 *
	 * <p>The default implementation, for gateways written before options existed, honours
	 * the timeout and the metadata and refuses a consistency requirement rather than drop
	 * it: an answer that silently ignores "after my write" would look correct and be stale.
	 */
	default <T extends QueryResponse<?>> CompletableFuture<T> queryWith(Query<T> query, QueryOptions options) {
		var opts = options == null ? QueryOptions.defaults() : options;
		if (opts.requiresConsistency()) {
			return CompletableFuture.failedFuture(new UnsupportedOperationException(getClass().getName()
					+ " does not support consistency requirements (QueryOptions.after/readYourWrites)"));
		}
		return query(query, opts.getMetadata(), null, opts.timeoutMillis(this), TimeUnit.MILLISECONDS);
	}

	/** {@link #stream(Query, QueryOptions)} with {@link QueryOptions#defaults()}. */
	default <V extends View> QueryStream<V> stream(Query<? extends QueryResponse<V>> query) {
		return stream(query, QueryOptions.defaults());
	}

	/**
	 * The views of a query's answer, pulled as they are consumed: a large result never
	 * needs to fit in this bundle's memory, nor in the broker's. Works for any query
	 * handler, whatever it answers with ({@code Single}, {@code Multiple}, {@code Page},
	 * {@code Streamed}); only a {@code Streamed} answer is also lazy on the answering side.
	 * Close the returned stream (try-with-resources).
	 *
	 * <p>The default implementation, for gateways that cannot stream, asks the query the
	 * plain way and iterates the whole answer.
	 */
	default <V extends View> QueryStream<V> stream(Query<? extends QueryResponse<V>> query, QueryOptions options) {
		var opts = options == null ? QueryOptions.defaults() : options;
		try {
			QueryResponse<?> answer = queryWith(query, opts).get(opts.timeoutMillis(this), TimeUnit.MILLISECONDS);
			return QueryStream.ofResponse(answer);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return QueryStream.failed(e);
		} catch (java.util.concurrent.ExecutionException e) {
			return QueryStream.failed(e.getCause());
		} catch (java.util.concurrent.TimeoutException e) {
			return QueryStream.failed(new QueryStream.QueryStreamException(
					"query " + query.getClass().getSimpleName() + " not answered in time", e));
		}
	}
}
