package com.evento.common.messaging.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.modeling.messaging.message.application.DomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.EventMessage;
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
	 * Sends a command asynchronously and returns a CompletableFuture that will be completed with the result.
	 *
	 * @param command         The command to send.
	 * @param metadata        The metadata associated with the command.
	 * @param handledMessage  The handled message.
	 * @param <R>             The type of the result.
	 * @return A CompletableFuture that will be completed with the result of the command execution.
	 */
	@Override
	public <R> CompletableFuture<R> send(Command command, Metadata metadata,
										 Message<?> handledMessage, long timeout, TimeUnit unit) {
		return send(command, metadata, handledMessage, timeout, unit, null);
	}

	/**
	 * Sends a command, optionally with an idempotency key carried on the command message
	 * ({@link com.evento.common.modeling.messaging.message.application.CommandMessage#getIdempotencyKey()}).
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <R> CompletableFuture<R> send(Command command, Metadata metadata,
										 Message<?> handledMessage, long timeout, TimeUnit unit,
										 String idempotencyKey) {
		return requestCommand(command, metadata, idempotencyKey, timeout, unit)
				.thenApply(e -> (R) resultOf(e));
	}

	/**
	 * Like {@link #send(Command, Metadata, Message, long, TimeUnit, String)}, also reporting where
	 * the broker stored the command's event (it says so from 2.6; an older broker answers
	 * {@link com.evento.common.modeling.messaging.query.ConsistencyToken#NONE}).
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <R> CompletableFuture<Tracked<R>> sendTracked(Command command, Metadata metadata,
														  Message<?> handledMessage, long timeout, TimeUnit unit,
														  String idempotencyKey) {
		return requestCommand(command, metadata, idempotencyKey, timeout, unit)
				.thenApply(e -> new Tracked<>((R) resultOf(e),
						e instanceof EventMessage<?> em ? em.getStoredAt() : null));
	}

	private CompletableFuture<Serializable> requestCommand(Command command, Metadata metadata, String idempotencyKey,
													 long timeout, TimeUnit unit) {
		try
		{
			var message = command instanceof DomainCommand ?
					new DomainCommandMessage((DomainCommand) command) :
					new ServiceCommandMessage((ServiceCommand) command);
			message.setMetadata(metadata);
			message.setIdempotencyKey(idempotencyKey);
			return eventoServer.request(message, timeout, unit);
		} catch (Exception e)
		{
			return CompletableFuture.failedFuture(e);
		}
	}

	/** The command's result as callers have always received it: the event's payload. */
	private static Object resultOf(Object e) {
		if (e instanceof EventMessage<?> em) {
			// v2 path: the handler bundle returns a ServiceEventMessage/DomainEventMessage
			// wrapper. Extract the actual event payload, mirroring what the v1 server did
			// (it called getSerializedPayload().getSerializedObject() before forwarding).
			var sp = em.getSerializedPayload();
			return sp == null ? null : sp.getObject();
		}
		if (!(e instanceof String)) {
			return e;
		}
		try {
			return ObjectMapperUtils.getPayloadObjectMapper()
					.readValue(e.toString(), Serializable.class);
		} catch (JsonProcessingException ex) {
			throw new CompletionException(ex);
		}
	}

	/**
	 * {@code true} when this class's six-argument {@code send} is the one its five-argument
	 * {@code send} goes through, which holds for {@code CommandGatewayImpl} itself. A subclass
	 * written before idempotency keys that overrides only the five-argument {@code send} (to
	 * add headers, metrics, a fallback) would be bypassed by a keyed send, which goes straight to
	 * the inherited six-argument one: for such a subclass this answers {@code false}, and a
	 * consumer handler's gateway then sends without a derived key, through the override.
	 * Computed once per class.
	 */
	@Override
	public boolean supportsIdempotencyKeys() {
		return KEYED_SEND_NOT_BYPASSED.get(getClass());
	}

	private static final ClassValue<Boolean> KEYED_SEND_NOT_BYPASSED = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			try {
				var five = type.getMethod("send", Command.class, Metadata.class, Message.class,
						long.class, TimeUnit.class);
				var six = type.getMethod("send", Command.class, Metadata.class, Message.class,
						long.class, TimeUnit.class, String.class);
				// The six-argument send must be declared no higher in the hierarchy than the
				// five-argument one, or a keyed send skips the five-argument override.
				return five.getDeclaringClass().isAssignableFrom(six.getDeclaringClass());
			} catch (NoSuchMethodException e) {
				return false;
			}
		}
	};
}
