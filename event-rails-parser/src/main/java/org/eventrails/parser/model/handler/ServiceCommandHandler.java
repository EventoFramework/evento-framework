package org.eventrails.parser.model.handler;

import org.eventrails.parser.model.payload.*;

import java.util.ArrayList;
import java.util.Collection;

public class ServiceCommandHandler extends Handler<ServiceCommand> implements HasCommandInvocations, HasQueryInvocations {
	private final ServiceEvent producedEvent;
	public ServiceCommandHandler(ServiceCommand payload, ServiceEvent producedEvent) {
		super(payload);
		this.producedEvent = producedEvent;
	}

	public ServiceEvent getProducedEvent() {
		return producedEvent;
	}

	private ArrayList<Command> invokedCommands = new ArrayList<>();
	private ArrayList<Query> invokedQueries = new ArrayList<>();


	@Override
	public void addCommandInvocation(Command command) {
		invokedCommands.add(command);
	}

	@Override
	public Collection<Command> getCommandInvocations() {
		return invokedCommands;
	}

	@Override
	public void addQueryInvocation(Query query) {
		invokedQueries.add(query);

	}

	@Override
	public Collection<Query> getQueryInvocations() {
		return invokedQueries;
	}
}
