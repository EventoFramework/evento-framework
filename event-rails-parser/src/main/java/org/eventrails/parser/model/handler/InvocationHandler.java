package org.eventrails.parser.model.handler;

import org.eventrails.parser.model.payload.Command;
import org.eventrails.parser.model.payload.Event;
import org.eventrails.parser.model.payload.Invocation;
import org.eventrails.parser.model.payload.Query;

import java.util.ArrayList;
import java.util.Collection;

public class InvocationHandler extends Handler<Invocation> implements HasCommandInvocations, HasQueryInvocations {
	private ArrayList<Command> invokedCommands = new ArrayList<>();
	private ArrayList<Query> invokedQueries = new ArrayList<>();


	public InvocationHandler(Invocation payload) {
		super(payload);
	}

	public InvocationHandler() {
	}

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
