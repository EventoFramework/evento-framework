package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Query;

import java.util.Map;

/**
 * HasQueryInvocations is an interface that represents an entity capable of adding and retrieving query invocations.
 */
public interface HasQueryInvocations {

	/**
	 * Adds a query invocation to the list of query invocations.
	 *
	 * @param query The query object to be added.
	 * @param line  The line number where the query invocation is made.
	 */
	void addQueryInvocation(Query query, int line);

	/**
	 * Retrieves a map of query invocations.
	 *
	 * @return The map of query invocations where the key is the line number of the invocation and the value is the Query object representing the invocation.
	 */
	Map<Integer, Query> getQueryInvocations();
}
