package org.eventrails.application.server.jgroups;

import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.messaging.payload.Command;
import org.jgroups.JChannel;
import org.jgroups.blocks.MessageDispatcher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class JGroupsCommandGateway implements CommandGateway {

	private final MessageDispatcher server;

	public JGroupsCommandGateway(JChannel jChannel) {
		server = new MessageDispatcher(jChannel);
	}

	@Override
	public <R> CompletableFuture<R> send(Command command) {
		return null;
	}

	@Override
	public <R> R sendAndWait(Command command) {
		return null;
	}

	@Override
	public <R> R sendAndWait(Command command, long timeout, TimeUnit unit) {
		return null;
	}
}
