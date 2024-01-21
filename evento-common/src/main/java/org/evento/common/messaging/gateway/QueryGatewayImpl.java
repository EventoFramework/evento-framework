package org.evento.common.messaging.gateway;

import org.evento.common.messaging.bus.EventoServer;
import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.messaging.message.application.Metadata;
import org.evento.common.modeling.messaging.message.application.QueryMessage;
import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.QueryResponse;
import org.evento.common.modeling.messaging.query.SerializedQueryResponse;

import java.util.concurrent.CompletableFuture;

/**
 * The QueryGatewayImpl class implements the QueryGateway interface
 * and provides a way to send queries and receive the corresponding responses asynchronously.
 */
public class QueryGatewayImpl implements QueryGateway {
	private final EventoServer eventoServer;

	/**
	 * The QueryGatewayImpl class implements the QueryGateway interface
	 * and provides a way to send queries and receive the corresponding responses asynchronously.
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
																   Message<?> handledMessage) {
		try
		{
			var message = new QueryMessage<>((query));
			message.setMetadata(metadata);
			return eventoServer.request(message).thenApply(r -> ((T) ((SerializedQueryResponse<?>) r).getObject()));
		} catch (Exception e)
		{
			var future = new CompletableFuture<T>();
			future.completeExceptionally(e);
			return future;
		}
	}
}
