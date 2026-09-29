package com.evento.common.messaging.gateway;

import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.common.modeling.messaging.payload.Command;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The CommandGateway interface defines methods for sending commands and interacting with a command handler.
 */
public interface CommandGateway extends Gateway {


	/**
	 * Sends a command asynchronously and returns a CompletableFuture that will eventually hold the result of the command execution.
	 *
	 * @param command the command to be sent
	 * @param <R>     the type of the expected result
	 * @return a CompletableFuture that will eventually hold the result of the command execution
	 */
	default <R> CompletableFuture<R> send(Command command) {
		return send(command, null, null, getDefaultTimeoutTime(), getDefaultTimeoutUnit());
	}


	/**
	 * Sends a command asynchronously and returns a CompletableFuture that will eventually hold the result of the command execution.
	 *
	 * @param command the command to be sent
	 * @param timeout the maximum time to wait for the command execution to complete
	 * @param unit    the time unit of the timeout parameter
	 * @param <R>     the type of the expected result
	 * @return a CompletableFuture that will eventually hold the result of the command execution
	 */
	default <R> CompletableFuture<R> send(Command command, long timeout, TimeUnit unit) {
		return send(command, null, null, timeout, unit);
	}


	/**
	 * Sends a command asynchronously with optional metadata and returns a CompletableFuture that will eventually hold the result of the command execution.
	 *
	 * @param command  the command to be sent
	 * @param metadata optional metadata associated with the command
	 * @param <R>      the type of the expected result
	 * @return a CompletableFuture that will eventually hold the result of the command execution
	 */
	default <R> CompletableFuture<R> send(Command command, Metadata metadata) {
		return send(command, metadata, null, getDefaultTimeoutTime(), getDefaultTimeoutUnit());
	}

	/**
	 * Sends a command asynchronously with optional metadata and returns a CompletableFuture that will eventually hold the result of the command execution.
	 *
	 * @param command  the command to be sent
	 * @param metadata optional metadata associated with the command
	 * @param <R>      the type of the expected result
	 * @return a CompletableFuture that will eventually hold the result of the command execution
	 */
	default <R> CompletableFuture<R> send(Command command, Metadata metadata, long timeout, TimeUnit unit) {
		return send(command, metadata, null, timeout, unit);
	}

	/**
	 * Sends a command and waits for its execution to complete.
	 *
	 * @param command          the command to be sent
	 * @param metadata         optional metadata associated with the command
	 * @param handledMessage   the handled message (optional)
	 * @param <R>              the type of the expected result
	 * @return the result of the command execution
	 */
    default <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage){
		return send(command, metadata, handledMessage, getDefaultTimeoutTime(), getDefaultTimeoutUnit());
	}

	/**
	 * Sends a command and waits for its execution to complete.
	 *
	 * @param command          the command to be sent
	 * @param metadata         optional metadata associated with the command
	 * @param handledMessage   the handled message (optional)
	 * @param timeout       the maximum time to wait for the command execution to complete
	 * @param unit          the time unit of the timeout parameter
	 * @param <R>              the type of the expected result
	 * @return the result of the command execution
	 */
    <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage, long timeout, TimeUnit unit);

	/**
	 * Sends a command that is applied at most once per {@code idempotencyKey}, with the default timeout.
	 *
	 * @see #sendIdempotent(Command, String, Metadata, long, TimeUnit)
	 */
	default <R> CompletableFuture<R> sendIdempotent(Command command, String idempotencyKey) {
		return send(command, null, null, getDefaultTimeoutTime(), getDefaultTimeoutUnit(), idempotencyKey);
	}

	/**
	 * Sends a command that is applied at most once per {@code idempotencyKey}.
	 *
	 * <p>The broker stores the key with the command's event. Sending again with the same key —
	 * after a timeout, a {@code BrokerChangedException}, a crash of the caller, through the same
	 * or another server of the cluster — returns the event stored by the first successful
	 * execution and runs no handler. Use it whenever a failed send is retried: every failure
	 * that is not a rejection by the handler is indeterminate (the command may have been
	 * applied), and only a key makes the retry safe.
	 *
	 * <p>The key names one command: mint it once per intended effect (for example from an HTTP
	 * {@code Idempotency-Key} header or a business id) and reuse it only for retries of that
	 * command. A key reused for a different command replays the first command's event. Only a
	 * success is recorded: a command rejected by its handler can be retried with the same key.
	 * Keys are kept as long as the event (no expiry).
	 *
	 * @param command        the command to be sent
	 * @param idempotencyKey the key, at most 255 characters; {@code null} or blank sends without one
	 * @param metadata       optional metadata
	 * @param timeout        the maximum time to wait for the command execution to complete
	 * @param unit           the time unit of the timeout parameter
	 * @param <R>            the type of the expected result
	 * @return the result of the command execution (the stored event's payload on a replay)
	 */
	default <R> CompletableFuture<R> sendIdempotent(Command command, String idempotencyKey, Metadata metadata,
												   long timeout, TimeUnit unit) {
		return send(command, metadata, null, timeout, unit, idempotencyKey);
	}

	/**
	 * The full form of {@link #send(Command, Metadata, Message, long, TimeUnit)} with an explicit
	 * idempotency key ({@code null} = none; see {@link #sendIdempotent(Command, String, Metadata, long, TimeUnit)}).
	 *
	 * <p>The default implementation supports only {@code null}: a gateway written before
	 * idempotency keys existed fails loudly on a key rather than silently dropping it.
	 * {@code CommandGatewayImpl} and the bundle's component gateways override it.
	 *
	 * @throws UnsupportedOperationException (in the returned future) when a key is given to a
	 *                                       gateway that does not implement keys
	 */
	default <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage,
										  long timeout, TimeUnit unit, String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return send(command, metadata, handledMessage, timeout, unit);
		}
		return CompletableFuture.failedFuture(new UnsupportedOperationException(
				getClass().getName() + " does not support idempotency keys"));
	}

}
