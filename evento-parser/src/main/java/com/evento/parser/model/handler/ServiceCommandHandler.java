package com.evento.parser.model.handler;

import com.evento.parser.model.payload.Command;
import com.evento.parser.model.payload.Query;
import com.evento.parser.model.payload.ServiceCommand;
import com.evento.parser.model.payload.ServiceEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * The ServiceCommandHandler class represents a handler for service commands.
 * It extends the Handler class and implements the HasCommandInvocations and HasQueryInvocations interfaces.
 * <p>
 * It keeps track of the command and query invocations and provides methods to add and retrieve them.
 */
public class ServiceCommandHandler extends Handler<ServiceCommand> implements HasCommandInvocations, HasQueryInvocations {
	private ServiceEvent producedEvent;
	private HashMap<Integer, Command> invokedCommands = new HashMap<>();
	private HashMap<Integer, Query> invokedQueries = new HashMap<>();

	/**
	 * Constructs a new ServiceCommandHandler object with the given payload, producedEvent, and line number.
	 *
	 * @param payload        The ServiceCommand payload object.
	 * @param producedEvent  The ServiceEvent produced by the handler.
	 * @param line           The line number where the handler is invoked.
	 */
	public ServiceCommandHandler(ServiceCommand payload, ServiceEvent producedEvent, int line) {
		super(payload, line);
		this.producedEvent = producedEvent;
	}

	/**
	 * Constructs a new ServiceCommandHandler object.
	 * This method initializes the ServiceCommandHandler object without any parameters.
	 * It is primarily used for creating an instance of ServiceCommandHandler without any initial payload or produced event.
	 */
	public ServiceCommandHandler() {
	}

	/**
	 * Retrieves the produced event by the ServiceCommandHandler.
	 *
	 * @return the ServiceEvent produced by the handler
	 */
	public ServiceEvent getProducedEvent() {
		return producedEvent;
	}

	/**
	 * Sets the produced event by the ServiceCommandHandler.
	 *
	 * @param producedEvent The ServiceEvent object representing the produced event.
	 */
	public void setProducedEvent(ServiceEvent producedEvent) {
		this.producedEvent = producedEvent;
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
	 * Retrieves a map of the command invocations.
	 *
	 * @return A map of command invocations, where the key is the line number and the value is the Command object.
	 */
	public HashMap<Integer, Command> getInvokedCommands() {
		return invokedCommands;
	}

	/**
	 * Sets the map of invoked commands.
	 *
	 * @param invokedCommands The new map of invoked commands.
	 *                        The key is the line number of the command invocation,
	 *                        and the value is the Command object representing the command.
	 */
	public void setInvokedCommands(HashMap<Integer, Command> invokedCommands) {
		this.invokedCommands = invokedCommands;
	}

	/**
	 * Retrieves a map of the queries that have been invoked.
	 *
	 * @return A HashMap of query invocations, where the key is the line number and the value is the Query object representing the invocation.
	 */
	public HashMap<Integer, Query> getInvokedQueries() {
		return invokedQueries;
	}

	/**
	 * Sets the map of invoked queries.
	 *
	 * @param invokedQueries The new map of invoked queries.
	 *                       The key is the line number of the query invocation,
	 *                       and the value is the Query object representing the query.
	 */
	public void setInvokedQueries(HashMap<Integer, Query> invokedQueries) {
		this.invokedQueries = invokedQueries;
	}
}
