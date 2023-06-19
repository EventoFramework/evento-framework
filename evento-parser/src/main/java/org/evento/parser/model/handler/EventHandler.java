package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Command;
import org.evento.parser.model.payload.Event;
import org.evento.parser.model.payload.Query;

import java.util.HashMap;
import java.util.Map;

public class EventHandler extends Handler<Event> implements HasQueryInvocations, HasCommandInvocations {

	private HashMap<Integer, Query> invokedQueries = new HashMap<>();
	private HashMap<Integer, Command> invokedCommands = new HashMap<>();

	public EventHandler(Event payload) {
		super(payload);
	}
	public EventHandler() {
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

	@Override
	public void addCommandInvocation(Command command, int line) {
		invokedCommands.put(line, command);
	}

	@Override
	public Map<Integer, Command> getCommandInvocations() {
		return invokedCommands;
	}

	public HashMap<Integer, Command> getInvokedCommands() {
		return invokedCommands;
	}

	public void setInvokedCommands(HashMap<Integer, Command> invokedCommands) {
		this.invokedCommands = invokedCommands;
	}
}
