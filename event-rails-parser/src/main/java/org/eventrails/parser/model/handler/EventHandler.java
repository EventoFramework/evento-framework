package org.eventrails.parser.model.handler;

import org.eventrails.parser.model.payload.Command;
import org.eventrails.parser.model.payload.Event;
import org.eventrails.parser.model.payload.Query;

import java.util.ArrayList;
import java.util.Collection;

public class EventHandler extends Handler<Event> implements HasQueryInvocations{

	public EventHandler(Event payload) {
		super(payload);
	}

	private ArrayList<Query> invokedQueries = new ArrayList<>();

	@Override
	public void addQueryInvocation(Query query) {
		invokedQueries.add(query);
	}

	@Override
	public Collection<Query> getQueryInvocations() {
		return invokedQueries;
	}
}
