package org.evento.common.messaging.gateway;

import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.messaging.message.application.Metadata;
import org.evento.common.modeling.messaging.payload.Command;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The CommandGateway interface defines methods for sending commands and interacting with a command handler.
 */
public interface CommandGateway {

	/**
	 * Sends a command and waits for its execution to complete.
	 *
	 * @param command the command to be sent
	 * @param <R> the type of the expected result
	 * @return the result of the command execution
	 */
	default <R> R sendAndWait(Command command) {
		return sendAndWait(command, null);
	}

	/**
	 * Sends a command and waits for its execution to complete.
	 *
	 * @param command the command to be sent
	 * @param timeout the maximum time to wait for the command execution to complete
	 * @param unit    the time unit of the timeout parameter
	 * @param <R>     the type of the expected result
	 * @return the result of the command execution
	 */
	default <R> R sendAndWait(Command command, long timeout, TimeUnit unit) {
		return sendAndWait(command, null, timeout, unit);
	}

	/**
	 * Sends a command asynchronously and returns a CompletableFuture that will eventually hold the result of the command execution.
	 *
	 * @param command the command to be sent
	 * @param <R>     the type of the expected result
	 * @return a CompletableFuture that will eventually hold the result of the command execution
	 */
	default <R> CompletableFuture<R> send(Command command) {
		return send(command, null);
	}



	/**
	 * Sends a command and waits for its execution to complete.
	 *
	 * @param command the command to be sent
	 * @param metadata optional metadata associated with the command
	 * @param <R> the type of the expected result
	 * @return the result of the command execution
	 */
	default <R> R sendAndWait(Command command, Metadata metadata) {
		return sendAndWait(command, metadata, null);
	}


	/**
	 * Sends a command and waits for its execution to complete.
	 *
	 * @param command  the command to be sent
	 * @param metadata optional metadata associated with the command
	 * @param timeout  the maximum time to wait for the command execution to complete
	 * @param unit     the time unit of the timeout parameter
	 * @param <R>      the type of the expected result
	 * @return the result of the command execution
	 */
	default <R> R sendAndWait(Command command, Metadata metadata, long timeout, TimeUnit unit) {
		return sendAndWait(command, metadata, null, timeout, unit);
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
		return send(command, metadata, null);
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
	<R> R sendAndWait(Command command, Metadata metadata, Message<?> handledMessage);

	/**
	 * Sends a command and waits for its execution to complete.
	 *
	 * @param command       the command to be sent
	 * @param metadata      optional metadata associated with the command
	 * @param handledMessage   the handled message (optional)
	 * @param timeout       the maximum time to wait for the command execution to complete
	 * @param unit          the time unit of the timeout parameter
	 * @param <R>           the type of the expected result
	 * @return the result of the command execution
	 */
	<R> R sendAndWait(Command command, Metadata metadata, Message<?> handledMessage, long timeout, TimeUnit unit);

	/**
	 * Sends a command and waits for its execution to complete.
	 *
	 * @param command          the command to be sent
	 * @param metadata         optional metadata associated with the command
	 * @param handledMessage   the handled message (optional)
	 * @param <R>              the type of the expected result
	 * @return the result of the command execution
	 */
    <R> CompletableFuture<R> send(Command command, Metadata metadata, Message<?> handledMessage);

}
