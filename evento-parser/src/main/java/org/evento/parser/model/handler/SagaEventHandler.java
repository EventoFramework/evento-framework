package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Command;
import org.evento.parser.model.payload.Event;
import org.evento.parser.model.payload.Query;

import java.util.HashMap;
import java.util.Map;

/**
 * The SagaEventHandler class represents a handler for saga events. It extends the Handler class and implements the HasCommandInvocations and HasQueryInvocations interfaces.
 */
public class SagaEventHandler extends Handler<Event> implements HasCommandInvocations, HasQueryInvocations {

	private HashMap<Integer, Command> invokedCommands = new HashMap<>();
	private HashMap<Integer, Query> invokedQueries = new HashMap<>();

	private String associationProperty;

	/**
	 * Constructs a new SagaEventHandler object with the specified event payload,
	 * association property, and line number.
	 *
	 * @param payload              The event payload.
	 * @param associationProperty  The association property.
	 * @param line                 The line number where the handler is invoked.
	 */
	public SagaEventHandler(Event payload, String associationProperty, int line) {
		super(payload, line);
		this.associationProperty = associationProperty;
	}

	/**
	 * The SagaEventHandler class represents a handler for saga events.
	 * It extends the Handler class and implements the HasCommandInvocations and HasQueryInvocations interfaces.
	 */
	public SagaEventHandler() {
	}

	@Override
	public void addCommandInvocation(Command command, int line) {
		invokedCommands.put(line, command);
	}

	@Override
	public Map<Integer, Command> getCommandInvocations() {
		return invokedCommands;
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
	 * Retrieves the association property of the SagaEventHandler.
	 *
	 * @return The association property.
	 */
	public String getAssociationProperty() {
		return associationProperty;
	}

	/**
	 * Sets the association property of the SagaEventHandler.
	 *
	 * @param associationProperty The association property to set.
	 */
	public void setAssociationProperty(String associationProperty) {
		this.associationProperty = associationProperty;
	}

	/**
	 * Retrieves a map of the commands that have been invoked by the SagaEventHandler.
	 *
	 * @return A HashMap<Integer, Command> representing the invoked commands. The key is the line number of the invocation and the value is the Command object.
	 */
	public HashMap<Integer, Command> getInvokedCommands() {
		return invokedCommands;
	}

	/**
	 * Sets the invoked commands for the SagaEventHandler.
	 *
	 * @param invokedCommands The HashMap of invoked commands. The key represents the line number of the invocation, and the value represents the Command object.
	 */
	public void setInvokedCommands(HashMap<Integer, Command> invokedCommands) {
		this.invokedCommands = invokedCommands;
	}

	/**
	 * Retrieves a HashMap of the queries that have been invoked by the SagaEventHandler object.
	 *
	 * @return A HashMap<Integer, Query> representing the invoked queries. The key is the line number of the invocation and the value is the Query object.
	 */
	public HashMap<Integer, Query> getInvokedQueries() {
		return invokedQueries;
	}

	/**
	 * Sets the invoked queries for the SagaEventHandler.
	 *
	 * @param invokedQueries The HashMap of invoked queries. The key represents the line number of the invocation, and the value represents the Query object.
	 */
	public void setInvokedQueries(HashMap<Integer, Query> invokedQueries) {
		this.invokedQueries = invokedQueries;
	}
}
