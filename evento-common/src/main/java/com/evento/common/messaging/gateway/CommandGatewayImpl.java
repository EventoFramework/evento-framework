package com.evento.common.messaging.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.modeling.messaging.message.application.DomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.message.application.ServiceCommandMessage;
import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.messaging.payload.DomainCommand;
import com.evento.common.modeling.messaging.payload.ServiceCommand;
import com.evento.common.serialization.ObjectMapperUtils;

import java.io.Serializable;
import java.util.concurrent.*;

/**
 * The CommandGatewayImpl class is an implementation of the CommandGateway interface.
 * It provides methods for sending commands and interacting with a command handler.
 */
public class CommandGatewayImpl implements CommandGateway {
	private final EventoServer eventoServer;

	/**
	 * The CommandGatewayImpl class is an implementation of the CommandGateway interface.
	 * It allows sending commands and interacting with a command handler.
     * @param eventoServer an evento server connection instance
     */
	public CommandGatewayImpl(EventoServer eventoServer) {
		this.eventoServer = eventoServer;
	}

	/**
	 * Sends a command synchronously and waits for the result.
	 *
	 * @param command         The command to send.
	 * @param metadata        The metadata associated with the command.
	 * @param handledMessage The handled message.
	 * @param <R>             The type of the result.
	 * @return The result of the command execution.
	 * @throws RuntimeException if an error occurs while executing the command.
	 */
	@SuppressWarnings("unchecked")
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

	/**
	 * Sends a command synchronously and waits for the result.
	 *
	 * @param command         The command to send.
	 * @param metadata        The metadata associated with the command.
	 * @param handledMessage  The handled message.
	 * @param timeout         The maximum time to wait for the result.
	 * @param unit            The time unit of the timeout argument.
	 * @param <R>             The type of the result.
	 * @return The result of the command execution.
	 * @throws RuntimeException if an error occurs while executing the command.
	 */
	@SuppressWarnings("unchecked")
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

	/**
	 * Sends a command asynchronously and returns a CompletableFuture that will be completed with the result.
	 *
	 * @param command         The command to send.
	 * @param metadata        The metadata associated with the command.
	 * @param handledMessage  The handled message.
	 * @param <R>             The type of the result.
	 * @return A CompletableFuture that will be completed with the result of the command execution.
	 */
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
			return (CompletableFuture<R>) eventoServer.request(message).thenApply(e -> {
				try {
					return ObjectMapperUtils.getPayloadObjectMapper()
							.readValue(e.toString(), Serializable.class);
				} catch (JsonProcessingException ex) {
					throw new CompletionException(ex);
				}
			});
		} catch (Exception e)
		{
			var future = new CompletableFuture<R>();
			future.completeExceptionally(e);
			return future;
		}
	}
}
