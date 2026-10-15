package com.evento.common.messaging.gateway;

import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.message.application.QueryMessage;
import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.payload.View;
import com.evento.common.modeling.messaging.query.QueryResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The QueryGatewayImpl class implements the QueryGateway interface
 * and provides a way to send queries and receive the corresponding responses asynchronously.
 */
public class QueryGatewayImpl implements QueryGateway {
	private final EventoServer eventoServer;

	/**
	 * The QueryGatewayImpl class implements the QueryGateway interface
	 * and provides a way to send queries and receive the corresponding responses asynchronously.
     * @param eventoServer an instance of evento server connection
     */
	public QueryGatewayImpl(EventoServer eventoServer) {
		this.eventoServer = eventoServer;
	}

	/**
	 * Queries the system with the given Query object and returns a CompletableFuture representing the response.
	 *
	 * @param query          The Query object to be sent to the system.
	 * @param metadata       The metadata associated with the query.
	 * @param handledMessage The handled message associated with the query.
	 * @param <T>            The type parameter of the QueryResponse expected as the response.
	 * @return A CompletableFuture that resolves to the response of the query.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, Metadata metadata,
																   Message<?> handledMessage, long timeout, TimeUnit unit) {
		try
		{
			var message = new QueryMessage<>((query));
			message.setMetadata(metadata);
			return eventoServer.request(message, timeout, unit).thenApply(r -> (T) r);
		} catch (Exception e)
		{
			var future = new CompletableFuture<T>();
			future.completeExceptionally(e);
			return future;
		}
	}

	/**
	 * With {@link QueryOptions#readYourWrites()}, the answer reflects every write this
	 * process's commands made ({@link EventoServer#ownWrites()}), plus any token passed with
	 * {@link QueryOptions#after}.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T extends QueryResponse<?>> CompletableFuture<T> queryWith(Query<T> query, QueryOptions options) {
		try
		{
			var opts = options == null ? QueryOptions.defaults() : options;
			return eventoServer.request(message(query, opts), opts.timeoutMillis(this), TimeUnit.MILLISECONDS)
					.thenApply(r -> (T) r);
		} catch (Exception e)
		{
			return CompletableFuture.failedFuture(e);
		}
	}

	@Override
	public <V extends View> QueryStream<V> stream(Query<? extends QueryResponse<V>> query, QueryOptions options) {
		var opts = options == null ? QueryOptions.defaults() : options;
		QueryStream<V> streamed;
		try {
			streamed = eventoServer.streamQuery(message(query, opts), opts, opts.timeoutMillis(this));
		} catch (RuntimeException e) {
			return QueryStream.failed(e);
		}
		return streamed != null ? streamed : QueryGateway.super.stream(query, opts);
	}

	private QueryMessage<?> message(Query<?> query, QueryOptions opts) {
		var message = new QueryMessage<>(query);
		message.setMetadata(opts.getMetadata());
		var consistency = opts.getConsistency();
		if (opts.isReadYourWrites()) {
			consistency = consistency.merge(eventoServer.ownWrites());
		}
		message.setConsistency(consistency);
		return message;
	}
}
