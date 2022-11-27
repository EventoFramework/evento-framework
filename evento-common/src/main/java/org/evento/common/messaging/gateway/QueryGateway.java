package org.evento.common.messaging.gateway;

import org.evento.common.modeling.messaging.message.application.QueryMessage;
import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.QueryResponse;
import org.evento.common.modeling.messaging.query.SerializedQueryResponse;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.messaging.utils.RoundRobinAddressPicker;

import java.util.concurrent.CompletableFuture;

public class QueryGateway {
	private final MessageBus messageBus;
	private final String serverName;

	private final RoundRobinAddressPicker roundRobinAddressPicker;

	public QueryGateway(MessageBus messageBus, String serverName) {
		this.serverName = serverName;
		this.messageBus = messageBus;
		this.roundRobinAddressPicker = new RoundRobinAddressPicker(messageBus);
	}


	@SuppressWarnings("unchecked")
	public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query) {
		var future = new CompletableFuture<T>();
		try
		{
			messageBus.request(
					roundRobinAddressPicker.pickNodeAddress(serverName),
					new QueryMessage<>((query)),
					response -> {
						try
						{
							future.complete((T) ((SerializedQueryResponse<?>) response).getObject());
						}catch (Exception e){
							future.completeExceptionally(e);
						}
					},
					error -> {
						future.completeExceptionally(error.toThrowable());
					}
			);
			return future;
		} catch (Exception e)
		{
			future.completeExceptionally(e);
			return future;
		}
	}
}
