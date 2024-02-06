package com.evento.parser.model.handler;

import com.evento.parser.model.payload.Command;
import com.evento.parser.model.payload.Event;
import com.evento.parser.model.payload.Query;

import java.util.HashMap;
import java.util.Map;

/**
 * The EventHandler class represents an event handler that handles event payloads.
 * It extends the Handler class and implements the HasQueryInvocations and HasCommandInvocations interfaces.
 * It contains methods to add and retrieve query and command invocations.
 */
public class EventHandler extends Handler<Event> implements HasQueryInvocations, HasCommandInvocations {

	private HashMap<Integer, Query> invokedQueries = new HashMap<>();
	private HashMap<Integer, Command> invokedCommands = new HashMap<>();

	/**
	 * EventHandler represents an event handler that handles event payloads.
	 * It extends the Handler class and implements the HasQueryInvocations and HasCommandInvocations interfaces.
	 * It contains methods to add and retrieve query and command invocations.
     * @param payload the event
     * @param line the line on file
     */
	public EventHandler(Event payload, int line) {
		super(payload, line);
	}
	/**
	 * The EventHandler class represents an event handler that handles event payloads.
	 * It extends the Handler class and implements the HasQueryInvocations and HasCommandInvocations interfaces.
	 * It contains methods to add and retrieve query and command invocations.
	 */
	public EventHandler() {
	}

	/**
	 * Adds a query invocation to the EventHandler.
	 *
	 * @param query the Query object representing the query invocation
	 * @param line  the line number where the query invocation occurs
	 */
	@Override
	public void addQueryInvocation(Query query, int line) {
		invokedQueries.put(line, query);
	}

	/**
	 * Retrieves a map of query invocations.
	 *
	 * @return a {@code Map} containing the line numbers and corresponding {@code Query} objects of the invocations.
	 */
	@Override
	public Map<Integer, Query> getQueryInvocations() {
		return invokedQueries;
	}

	/**
	 * Retrieves a map of invoked queries.
	 *
	 * @return a {@code HashMap} containing the line numbers and corresponding {@code Query} objects of the invoked queries.
	 */
	public HashMap<Integer, Query> getInvokedQueries() {
		return invokedQueries;
	}

	/**
	 * Sets the map of invoked queries for the EventHandler.
	 *
	 * @param invokedQueries a {@code HashMap} containing the line numbers and corresponding {@code Query} objects
	 *                       of the invoked queries
	 */
	public void setInvokedQueries(HashMap<Integer, Query> invokedQueries) {
		this.invokedQueries = invokedQueries;
	}

	/**
	 * Add a command invocation to the EventHandler.
	 *
	 * @param command the Command object representing the command invocation
	 * @param line    the line number where the command invocation occurs
	 */
	@Override
	public void addCommandInvocation(Command command, int line) {
		invokedCommands.put(line, command);
	}

	/**
	 * Retrieves a map of command invocations.
	 *
	 * @return a {@code Map} containing the line numbers and corresponding {@code Command} objects of the invocations.
	 */
	@Override
	public Map<Integer, Command> getCommandInvocations() {
		return invokedCommands;
	}

	/**
	 * Retrieves a map of the invoked commands.
	 *
	 * @return a {@code HashMap} that contains the line numbers and corresponding {@code Command} objects of the invoked commands.
	 */
	public HashMap<Integer, Command> getInvokedCommands() {
		return invokedCommands;
	}

	/**
	 * Sets the map of invoked commands for the EventHandler.
	 *
	 * @param invokedCommands a HashMap containing the line numbers and corresponding Command objects
	 */
	public void setInvokedCommands(HashMap<Integer, Command> invokedCommands) {
		this.invokedCommands = invokedCommands;
	}
}
