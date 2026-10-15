package com.evento.common.messaging.gateway;

import com.evento.common.modeling.messaging.message.application.CommandMessage;
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
		return sendIdempotent(command, idempotencyKey, null, getDefaultTimeoutTime(), getDefaultTimeoutUnit());
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
	 * command, with the same command type and the same aggregate id. The broker records both
	 * with the key and answers a key reused by a different command (another type, another
	 * aggregate) with {@code IdempotencyKeyMismatchException}, never with the other command's
	 * event: mint the aggregate id of a creation from the key (or once, before the first attempt),
	 * not per attempt. A handler that forwards the key it received to a command of its own must
	 * derive a new one ({@code key + ":" + step}), since the outer command still owns its key.
	 * Only a success is recorded: a command rejected by its handler can be retried with the same
	 * key. Keys are kept as long as the event (no expiry).
	 *
	 * <p>Keys starting with {@code "evento:"} are reserved for the keys a consumer handler's
	 * gateway derives, and are refused here.
	 *
	 * @param command        the command to be sent
	 * @param idempotencyKey the key, at most 255 characters; {@code null} or blank sends without one
	 * @param metadata       optional metadata
	 * @param timeout        the maximum time to wait for the command execution to complete
	 * @param unit           the time unit of the timeout parameter
	 * @param <R>            the type of the expected result
	 * @return the result of the command execution (the stored event's payload on a replay); the
	 * future fails with {@link IllegalArgumentException} for a reserved or overlong key
	 */
	default <R> CompletableFuture<R> sendIdempotent(Command command, String idempotencyKey, Metadata metadata,
												   long timeout, TimeUnit unit) {
		try {
			CommandMessage.checkCallerIdempotencyKey(idempotencyKey);
		} catch (IllegalArgumentException e) {
			return CompletableFuture.failedFuture(e);
		}
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

	/**
	 * Whether this gateway carries the key given to
	 * {@link #send(Command, Metadata, Message, long, TimeUnit, String)} to the broker, along the
	 * same path as every other send. The gateway of a consumer handler derives a key for each
	 * command only when its target says so, and otherwise sends without one (the pre-G5
	 * behaviour): a gateway written before keys existed (a decorator, a test double) implements
	 * only the five-argument {@code send}, and must neither see every command fail nor be
	 * bypassed. An explicit {@code sendIdempotent} does not look at this: on a gateway that cannot
	 * carry keys it fails loudly.
	 *
	 * @return {@code false} by default; {@code true} for a gateway whose six-argument {@code send}
	 * carries the key and is the one its five-argument {@code send} goes through
	 */
	default boolean supportsIdempotencyKeys() {
		return false;
	}


	/** {@link #sendTracked(Command, Metadata, Message, long, TimeUnit, String)} with the defaults. */
	default <R> CompletableFuture<Tracked<R>> sendTracked(Command command) {
		return sendTracked(command, null, null, getDefaultTimeoutTime(), getDefaultTimeoutUnit(), null);
	}

	/** {@link #sendTracked(Command, Metadata, Message, long, TimeUnit, String)} with the default timeout. */
	default <R> CompletableFuture<Tracked<R>> sendTracked(Command command, Metadata metadata) {
		return sendTracked(command, metadata, null, getDefaultTimeoutTime(), getDefaultTimeoutUnit(), null);
	}

	/**
	 * Sends a command like {@link #send(Command, Metadata, Message, long, TimeUnit, String)} and
	 * also answers where its event was stored, so a later query can require to see it
	 * ({@code QueryOptions#after(Tracked#token())}).
	 *
	 * <p>The default implementation refuses: a gateway written before write positions existed
	 * cannot report them, and a token it made up would let a query answer stale.
	 */
	default <R> CompletableFuture<Tracked<R>> sendTracked(Command command, Metadata metadata,
														   Message<?> handledMessage, long timeout, TimeUnit unit,
														   String idempotencyKey) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException(getClass().getName()
				+ " does not report write positions (sendTracked)"));
	}
}
