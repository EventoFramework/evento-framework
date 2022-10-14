package org.eventrails.parser.model.handler;

import org.eventrails.parser.model.payload.Command;
import org.eventrails.parser.model.payload.Event;
import org.eventrails.parser.model.payload.Query;

import java.util.ArrayList;
import java.util.Collection;

public class SagaEventHandler extends Handler<Event> implements HasCommandInvocations, HasQueryInvocations {

	private ArrayList<Command> invokedCommands = new ArrayList<>();
	private ArrayList<Query> invokedQueries = new ArrayList<>();

	private String associationProperty;

	public SagaEventHandler(Event payload, String associationProperty) {
		super(payload);
		this.associationProperty = associationProperty;
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

	public String getAssociationProperty() {
		return associationProperty;
	}
}
