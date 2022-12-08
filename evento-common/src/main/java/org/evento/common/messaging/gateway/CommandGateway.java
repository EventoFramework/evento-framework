package org.evento.common.messaging.gateway;

import org.evento.common.modeling.messaging.message.application.DomainCommandMessage;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.common.modeling.messaging.message.application.SerializedPayload;
import org.evento.common.modeling.messaging.message.application.ServiceCommandMessage;
import org.evento.common.modeling.messaging.payload.Command;
import org.evento.common.modeling.messaging.payload.DomainCommand;
import org.evento.common.modeling.messaging.payload.ServiceCommand;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.messaging.utils.RoundRobinAddressPicker;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CommandGateway {
	private final MessageBus messageBus;
	private final String serverName;

	private final RoundRobinAddressPicker roundRobinAddressPicker;

	public CommandGateway(MessageBus messageBus, String serverName) {
		this.serverName = serverName;
		this.messageBus = messageBus;
		this.roundRobinAddressPicker = new RoundRobinAddressPicker(messageBus);
	}

	public <R> R sendAndWait(Command command) {
		try
		{
			return (R) send(command).get();
		} catch (InterruptedException | ExecutionException e)
		{
			throw new RuntimeException(e);
		}
	}

	public <R> R sendAndWait(Command command, long timeout, TimeUnit unit) {
		try
		{
			return (R) send(command).get(timeout, unit);
		} catch (InterruptedException | ExecutionException | TimeoutException e)
		{
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public <R> CompletableFuture<R> send(Command command) {
		var future = new CompletableFuture<R>();
		try
		{
			var address = roundRobinAddressPicker.pickNodeAddress(serverName);
			var message = command instanceof DomainCommand ?
					new DomainCommandMessage((DomainCommand) command) :
					new ServiceCommandMessage((ServiceCommand) command);
			messageBus.request(address,message,
					response -> {
						try{
							future.complete((R) ((EventMessage<?>) response).getSerializedPayload().getObject());
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
