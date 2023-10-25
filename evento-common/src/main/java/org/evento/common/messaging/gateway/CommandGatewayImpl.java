package org.evento.common.messaging.gateway;

import org.evento.common.messaging.bus.EventoServer;
import org.evento.common.modeling.messaging.message.application.*;
import org.evento.common.modeling.messaging.payload.Command;
import org.evento.common.modeling.messaging.payload.DomainCommand;
import org.evento.common.modeling.messaging.payload.ServiceCommand;

import java.util.concurrent.*;

public class CommandGatewayImpl implements CommandGateway {
	private final EventoServer eventoServer;

	public CommandGatewayImpl(EventoServer eventoServer) {
		this.eventoServer = eventoServer;
	}

	@Override
	public <R> R sendAndWait(Command command, Metadata metadata,
							 Message<?> handledMessage) {
		try
		{
			return (R) send(command, metadata, handledMessage).get();
		} catch (ExecutionException | CompletionException e )
		{
			throw new RuntimeException(e.getCause());
		}
		catch (InterruptedException e)
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
		}catch (ExecutionException | CompletionException e)
		{
			throw new RuntimeException(e.getCause());
		}
		catch (InterruptedException | TimeoutException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <R> CompletableFuture<R> send(Command command, Metadata metadata,
										 Message<?> handledMessage) {
		try
		{
			var message = command instanceof DomainCommand ?
					new DomainCommandMessage((DomainCommand) command) :
					new ServiceCommandMessage((ServiceCommand) command);
			message.setMetadata(metadata);
			return (CompletableFuture<R>) eventoServer.request(message);
		} catch (Exception e)
		{
			var future = new CompletableFuture<R>();
			future.completeExceptionally(e);
			return future;
		}
	}
}
