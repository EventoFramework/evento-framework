package org.eventrails.application.server.jgroups;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eventrails.modeling.gateway.QueryGateway;
import org.eventrails.modeling.messaging.message.QueryMessage;
import org.eventrails.modeling.messaging.payload.Query;
import org.eventrails.modeling.messaging.query.QueryResponse;
import org.eventrails.modeling.messaging.query.SerializedQueryResponse;
import org.eventrails.modeling.utils.ObjectMapperUtils;
import org.eventrails.modeling.messaging.message.bus.MessageBus;
import org.eventrails.modeling.messaging.message.bus.ServerHandleInvocationMessage;

import java.util.concurrent.CompletableFuture;

public class JGroupsQueryGateway implements QueryGateway {
	private final MessageBus messageBus;
	private final String serverName;

	private final ObjectMapper payloadMapper = ObjectMapperUtils.getPayloadObjectMapper();

	public JGroupsQueryGateway(MessageBus messageBus, String serverName) {
		this.serverName = serverName;
		this.messageBus = messageBus;
	}


	@Override
	@SuppressWarnings("unchecked")
	public <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query) {
		var future = new CompletableFuture<T>();
		try
		{
			messageBus.cast(
					messageBus.findNodeAddress(serverName),
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
