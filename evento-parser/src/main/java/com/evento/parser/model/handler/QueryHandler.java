package com.evento.parser.model.handler;

import com.evento.parser.model.payload.Query;

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

	/**
	 * Retrieves a map of invoked queries.
	 *
	 * @return A HashMap with Integer as the key representing the line number of the invocation,
	 * and Query as the value representing the invoked query.
	 */
	public HashMap<Integer, Query> getInvokedQueries() {
		return invokedQueries;
	}

	/**
	 * Sets the map of invoked queries.
	 *
	 * @param invokedQueries The map containing the invoked queries where the key is the line number of the invocation and the value is the Query object representing the invoked query
	 *.
	 */
	public void setInvokedQueries(HashMap<Integer, Query> invokedQueries) {
		this.invokedQueries = invokedQueries;
	}
}
