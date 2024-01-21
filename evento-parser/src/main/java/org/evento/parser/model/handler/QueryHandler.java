package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Query;

import java.util.HashMap;
import java.util.Map;

/**
 * The QueryHandler class represents a handler for Query payloads.
 * It extends the Handler class and implements the HasQueryInvocations interface.
 */
public class QueryHandler extends Handler<Query> implements HasQueryInvocations  {
	private HashMap<Integer, Query> invokedQueries = new HashMap<>();
	/**
	 * Constructs a new QueryHandler object with the specified payload and line number.
	 *
	 * @param payload The Query object representing the query payload.
	 * @param line    The line number where the query handler is invoked.
	 */
	public QueryHandler(Query payload, int line) {
		super(payload, line);
	}

	/**
	 * Constructs a new QueryHandler object.
	 */
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
