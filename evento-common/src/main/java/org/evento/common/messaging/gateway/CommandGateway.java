package org.evento.common.messaging.gateway;

import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.messaging.payload.Command;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public interface CommandGateway {
	default <R> R sendAndWait(Command command){
		return sendAndWait(command, null);
	}

	default <R> R sendAndWait(Command command, long timeout, TimeUnit unit){
		return sendAndWait(command, null, timeout, unit);
	}

	@SuppressWarnings("unchecked")
	default <R> CompletableFuture<R> send(Command command){
		return send(command, null);
	}


	default <R> R sendAndWait(Command command, HashMap<String, String> metadata){
		return sendAndWait(command, metadata, null);
	}

	default <R> R sendAndWait(Command command, HashMap<String, String> metadata, long timeout, TimeUnit unit) {
		return sendAndWait(command, metadata, null, timeout, unit);
	}

	@SuppressWarnings("unchecked")
	default <R> CompletableFuture<R> send(Command command, HashMap<String, String> metadata){
		return send(command, metadata, null);
	}


	<R> R sendAndWait(Command command, HashMap<String, String> metadata, Message<?> handledMessage);

	<R> R sendAndWait(Command command, HashMap<String, String> metadata, Message<?> handledMessage, long timeout, TimeUnit unit);

	@SuppressWarnings("unchecked")
	<R> CompletableFuture<R> send(Command command, HashMap<String, String> metadata, Message<?> handledMessage);

}
