package org.evento.parser.model.handler;

import org.evento.parser.model.payload.Command;
import org.evento.parser.model.payload.DomainCommand;
import org.evento.parser.model.payload.DomainEvent;
import org.evento.parser.model.payload.Query;

import java.util.HashMap;
import java.util.Map;
/**
 * Represents a command handler for an aggregate.
 * <p>
 * This class extends the {@link Handler} class and implements the {@link HasCommandInvocations}
 * and {@link HasQueryInvocations} interfaces. It provides methods to add and retrieve command and
 * query invocations, as well as to retrieve the produced event.
 */
public class AggregateCommandHandler extends Handler<DomainCommand>  implements HasCommandInvocations, HasQueryInvocations  {

	private final DomainEvent producedEvent;
	private final HashMap<Integer, Command> invokedCommands = new HashMap<>();
	private final HashMap<Integer, Query> invokedQueries = new HashMap<>();

	/**
	 * Creates a new instance of `AggregateCommandHandler` with the specified payload, produced event, and line number.
	 *
	 * @param payload       The payload of the command handler.
	 * @param producedEvent The event produced by the command handler.
	 * @param line          The line number where the command handler is invoked.
	 */
	public AggregateCommandHandler(DomainCommand payload, DomainEvent producedEvent, int line) {
		super(payload, line);
		this.producedEvent = producedEvent;
	}

	/**
	 * Retrieves the produced event by the command handler.
	 *
	 * @return The produced event by the command handler.
	 */
	public DomainEvent getProducedEvent() {
		return producedEvent;
	}

	/**
	 * Adds a command invocation to the map of invoked commands.
	 * The command is associated with a specified line number.
	 *
	 * @param command The command to be added.
	 * @param line    The line number where the command is invoked.
	 */
	@Override
	public void addCommandInvocation(Command command, int line) {
		invokedCommands.put(line, command);
	}

	/**
	 * Retrieves the map of command invocations.
	 * <p>
	 * This method returns a map containing the line number as the key and the associated Command object as the value.
	 *
	 * @return The map of command invocations.
	 */
	@Override
	public Map<Integer, Command> getCommandInvocations() {
		return invokedCommands;
	}
	@Override
	public void addQueryInvocation(Query query, int line) {
		invokedQueries.put(line, query);

	}

	/**
	 * Retrieves the map of query invocations.
	 * <p>
	 * This method returns a map containing the line number as the key and the associated Query object as the value.
	 *
	 * @return The map of query invocations.
	 */
	@Override
	public Map<Integer, Query> getQueryInvocations() {
		return invokedQueries;
	}
}
