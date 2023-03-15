package org.evento.common.messaging.gateway;

import org.evento.common.modeling.messaging.payload.Command;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public interface CommandGateway {
	<R> R sendAndWait(Command command);

	<R> R sendAndWait(Command command, long timeout, TimeUnit unit);

	@SuppressWarnings("unchecked")
	<R> CompletableFuture<R> send(Command command);
}
