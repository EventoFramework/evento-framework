package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Command;
import org.evento.parser.model.payload.Query;
import org.evento.parser.model.payload.ServiceCommand;
import org.evento.parser.model.payload.ServiceEvent;

import java.util.HashMap;
import java.util.Map;

public class ServiceCommandHandler extends Handler<ServiceCommand> implements HasCommandInvocations, HasQueryInvocations {
	private ServiceEvent producedEvent;
	private HashMap<Integer, Command> invokedCommands = new HashMap<>();
	private HashMap<Integer, Query> invokedQueries = new HashMap<>();

	public ServiceCommandHandler(ServiceCommand payload, ServiceEvent producedEvent) {
		super(payload);
		this.producedEvent = producedEvent;
	}

	public ServiceCommandHandler() {
	}

	public ServiceEvent getProducedEvent() {
		return producedEvent;
	}

	public void setProducedEvent(ServiceEvent producedEvent) {
		this.producedEvent = producedEvent;
	}

	@Override
	public void addCommandInvocation(Command command, int line) {
		invokedCommands.put(line, command);
	}

	@Override
	public Map<Integer, Command> getCommandInvocations() {
		return invokedCommands;
	}

	@Override
	public void addQueryInvocation(Query query, int line) {
		invokedQueries.put(line, query);

	}

	@Override
	public Map<Integer, Query> getQueryInvocations() {
		return invokedQueries;
	}

	public HashMap<Integer, Command> getInvokedCommands() {
		return invokedCommands;
	}

	public void setInvokedCommands(HashMap<Integer, Command> invokedCommands) {
		this.invokedCommands = invokedCommands;
	}

	public HashMap<Integer, Query> getInvokedQueries() {
		return invokedQueries;
	}

	public void setInvokedQueries(HashMap<Integer, Query> invokedQueries) {
		this.invokedQueries = invokedQueries;
	}
}
