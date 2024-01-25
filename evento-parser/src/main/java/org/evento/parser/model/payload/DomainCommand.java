package org.evento.parser.model.payload;

/**
 * The DomainCommand class represents a domain-specific command payload. It extends the Command class
 * and inherits its properties and methods.
 * <p>
 * Usage example:
 * <pre>
 *     DomainCommand command = new DomainCommand("commandName");
 * </pre>
 */
public class DomainCommand extends Command {
	/**
	 * Creates a new instance of {@code DomainCommand} with the specified name.
	 * <p>
	 * Usage example:
	 * <pre>
	 *     DomainCommand command = new DomainCommand("commandName");
	 * </pre>
	 * @param name the name of the domain-specific command
	 */
	public DomainCommand(String name) {
		super(name);
	}

	/**
	 * The DomainCommand class represents a domain-specific command payload. It extends the Command class
	 * and inherits its properties and methods.
	 * <p>
	 * Usage example:
	 * <pre>
	 *     DomainCommand command = new DomainCommand("commandName");
	 * </pre>
	 */
	public DomainCommand() { super();
	}
}
