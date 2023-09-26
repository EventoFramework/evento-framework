package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Query;

import java.util.HashMap;
import java.util.Map;

public class QueryHandler extends Handler<Query> implements HasQueryInvocations  {
	private HashMap<Integer, Query> invokedQueries = new HashMap<>();
	public QueryHandler(Query payload, int line) {
		super(payload, line);
	}

	public QueryHandler() {
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
