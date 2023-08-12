package org.evento.common.messaging.gateway;

import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.messaging.utils.RoundRobinAddressPicker;
import org.evento.common.modeling.messaging.message.application.*;
import org.evento.common.modeling.messaging.payload.Command;
import org.evento.common.modeling.messaging.payload.DomainCommand;
import org.evento.common.modeling.messaging.payload.ServiceCommand;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CommandGatewayImpl implements CommandGateway {
	private final MessageBus messageBus;
	private final String serverName;

	private final RoundRobinAddressPicker roundRobinAddressPicker;

	public CommandGatewayImpl(MessageBus messageBus, String serverName) {
		this.serverName = serverName;
		this.messageBus = messageBus;
		this.roundRobinAddressPicker = new RoundRobinAddressPicker(messageBus);
	}

	@Override
	public <R> R sendAndWait(Command command, Metadata metadata,
							 Message<?> handledMessage) {
		try
		{
			return (R) send(command, metadata, handledMessage).get();
		} catch (InterruptedException | ExecutionException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Override
	public <R> R sendAndWait(Command command, Metadata metadata,
							 Message<?> handledMessage, long timeout, TimeUnit unit) {
		try
		{
			return (R) send(command, metadata, handledMessage).get(timeout, unit);
		} catch (InterruptedException | ExecutionException | TimeoutException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <R> CompletableFuture<R> send(Command command, Metadata metadata,
										 Message<?> handledMessage) {
		var future = new CompletableFuture<R>();
		try
		{
			var address = roundRobinAddressPicker.pickNodeAddress(serverName);
			var message = command instanceof DomainCommand ?
					new DomainCommandMessage((DomainCommand) command) :
					new ServiceCommandMessage((ServiceCommand) command);
			message.setMetadata(metadata);
			messageBus.request(address, message,
					response -> {
						try
						{
							future.complete((R) ((EventMessage<?>) response).getSerializedPayload().getObject());
						} catch (Exception e)
						{
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
