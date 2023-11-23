package org.evento.common.messaging.gateway;

import org.evento.common.messaging.bus.EventoServer;
import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.messaging.message.application.Metadata;
import org.evento.common.modeling.messaging.message.application.QueryMessage;
import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.QueryResponse;
import org.evento.common.modeling.messaging.query.SerializedQueryResponse;

import java.util.concurrent.CompletableFuture;

public class QueryGatewayImpl implements QueryGateway {
	private final EventoServer eventoServer;

	public QueryGatewayImpl(EventoServer eventoServer) {
		this.eventoServer = eventoServer;
	}

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
