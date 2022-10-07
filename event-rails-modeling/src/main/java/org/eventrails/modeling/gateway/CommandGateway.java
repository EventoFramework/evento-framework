package org.eventrails.modeling.gateway;

import org.eventrails.modeling.messaging.payload.Command;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public interface CommandGateway {

	public <R> CompletableFuture<R> send(Command command);


	public default <R> R sendAndWait(Command command) {
		try
		{
			return (R) send(command).get();
		} catch (InterruptedException | ExecutionException e)
		{
			throw new RuntimeException(e);
		}
	}

	public default <R> R sendAndWait(Command command, long timeout, TimeUnit unit) {
		try
		{
			return (R) send(command).get(timeout, unit);
		} catch (InterruptedException | ExecutionException | TimeoutException e)
		{
			throw new RuntimeException(e);
		}
	}


}
