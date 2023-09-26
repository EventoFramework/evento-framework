package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Command;
import org.evento.parser.model.payload.DomainCommand;
import org.evento.parser.model.payload.DomainEvent;
import org.evento.parser.model.payload.Query;

import java.util.HashMap;
import java.util.Map;

public class AggregateCommandHandler extends Handler<DomainCommand>  implements HasCommandInvocations, HasQueryInvocations  {

	private DomainEvent producedEvent;
	private HashMap<Integer, Command> invokedCommands = new HashMap<>();
	private HashMap<Integer, Query> invokedQueries = new HashMap<>();

	public AggregateCommandHandler(DomainCommand payload, DomainEvent producedEvent, int line) {
		super(payload, line);
		this.producedEvent = producedEvent;
	}

	public AggregateCommandHandler() {
	}

	public DomainEvent getProducedEvent() {
		return producedEvent;
	}

	public void setProducedEvent(DomainEvent producedEvent) {
		this.producedEvent = producedEvent;
	}

	public HashMap<Integer, Command> getInvokedCommands() {
		return invokedCommands;
	}

	public void setInvokedCommands(HashMap<Integer, Command> invokedCommands) {
		this.invokedCommands = invokedCommands;
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

	public HashMap<Integer, Query> getInvokedQueries() {
		return invokedQueries;
	}

	public void setInvokedQueries(HashMap<Integer, Query> invokedQueries) {
		this.invokedQueries = invokedQueries;
	}
}
