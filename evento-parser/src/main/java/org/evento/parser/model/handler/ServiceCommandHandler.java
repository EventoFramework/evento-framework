package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Command;
import org.evento.parser.model.payload.Query;
import org.evento.parser.model.payload.ServiceCommand;
import org.evento.parser.model.payload.ServiceEvent;
import org.evento.parser.model.payload.*;

import java.util.ArrayList;
import java.util.Collection;

public class ServiceCommandHandler extends Handler<ServiceCommand> implements HasCommandInvocations, HasQueryInvocations {
	private ServiceEvent producedEvent;
	public ServiceCommandHandler(ServiceCommand payload, ServiceEvent producedEvent) {
		super(payload);
		this.producedEvent = producedEvent;
	}

	public ServiceCommandHandler() {
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

	public void setProducedEvent(ServiceEvent producedEvent) {
		this.producedEvent = producedEvent;
	}

	public ArrayList<Command> getInvokedCommands() {
		return invokedCommands;
	}

	public void setInvokedCommands(ArrayList<Command> invokedCommands) {
		this.invokedCommands = invokedCommands;
	}

	public ArrayList<Query> getInvokedQueries() {
		return invokedQueries;
	}

	public void setInvokedQueries(ArrayList<Query> invokedQueries) {
		this.invokedQueries = invokedQueries;
	}
}
