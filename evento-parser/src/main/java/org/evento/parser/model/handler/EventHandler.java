package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Event;
import org.evento.parser.model.payload.Query;

import java.util.ArrayList;
import java.util.Collection;

public class EventHandler extends Handler<Event> implements HasQueryInvocations{

	public EventHandler(Event payload) {
		super(payload);
	}

	public EventHandler() {
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
