package com.evento.parser.model.handler;

import com.evento.parser.model.payload.Command;
import com.evento.parser.model.payload.Invocation;
import com.evento.parser.model.payload.Query;

import java.util.HashMap;
import java.util.Map;

/**
 * The InvocationHandler class represents a handler for invocations.
 * It extends the Handler class and implements the HasCommandInvocations and HasQueryInvocations interfaces.
 */
public class InvocationHandler extends Handler<Invocation> implements HasCommandInvocations, HasQueryInvocations {
	private HashMap<Integer, Command> invokedCommands = new HashMap<>();
	private HashMap<Integer, Query> invokedQueries = new HashMap<>();


	/**
	 * The InvocationHandler class represents a handler for invocations.
	 * It extends the Handler class and implements the HasCommandInvocations and HasQueryInvocations interfaces.
     * @param payload the invocation payload
     * @param line line on the file
     */
	public InvocationHandler(Invocation payload, int line) {
		super(payload, line);
	}

	/**
	 * The InvocationHandler class represents a handler for invocations.
	 * It extends the Handler class and implements the HasCommandInvocations and HasQueryInvocations interfaces.
	 */
	public InvocationHandler() {
	}

	/**
	 * Adds a command invocation to the list of invoked commands.
	 *
	 * @param command The command object to be added.
	 * @param line    The line number where the command invocation occurs.
	 */
	@Override
	public void addCommandInvocation(Command command, int line) {
		invokedCommands.put(line, command);
	}

	/**
	 * This method returns a map of command invocations.
	 *
	 * @return A map of command invocations where the key is the line number and the value is the Command object.
	 */
	@Override
	public Map<Integer, Command> getCommandInvocations() {
		return invokedCommands;
	}

	/**
	 * Adds a query invocation to the list of invoked queries.
	 *
	 * @param query The query object to be added.
	 * @param line  The line number where the query invocation occurs.
	 */
	@Override
	public void addQueryInvocation(Query query, int line) {
		invokedQueries.put(line, query);

	}

	/**
	 * Returns a map of query invocations.
	 *
	 * @return A map where the key is the line number of the query invocation and the value is the Query object.
	 */
	@Override
	public Map<Integer, Query> getQueryInvocations() {
		return invokedQueries;
	}

	/**
	 * Returns a map of command invocations.
	 *
	 * @return A map of command invocations, where the key is the line number and the value is the Command object.
	 */
	public HashMap<Integer, Command> getInvokedCommands() {
		return invokedCommands;
	}

	/**
	 * Sets the map of invoked commands.
	 *
	 * @param invokedCommands The map of invoked commands to be set.
	 */
	public void setInvokedCommands(HashMap<Integer, Command> invokedCommands) {
		this.invokedCommands = invokedCommands;
	}

	/**
	 * Retrieves a map of invoked queries.
	 *
	 * @return A map where the key is the line number of the query invocation and the value is the Query object representing the invocation.
	 */
	public HashMap<Integer, Query> getInvokedQueries() {
		return invokedQueries;
	}

	/**
	 * Sets the map of invoked queries.
	 *
	 * @param invokedQueries The map of invoked queries to be set.
	 */
	public void setInvokedQueries(HashMap<Integer, Query> invokedQueries) {
		this.invokedQueries = invokedQueries;
	}
}
