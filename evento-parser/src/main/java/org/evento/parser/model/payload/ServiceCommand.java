package org.evento.parser.model.payload;

/**
 * The ServiceCommand class represents a command payload specific to a service.
 * It extends the Command class and inherits its properties and methods.
 */
public class ServiceCommand extends Command {
	/**
	 * Constructs a new {@code ServiceCommand} object with the specified name.
	 *
	 * @param name the name of the command
	 * @throws NullPointerException if the {@code name} is {@code null}
	 */
	public ServiceCommand(String name) {
		super(name);
	}

	/**
	 * The ServiceCommand class represents a command payload specific to a service.
	 * It extends the Command class and inherits its properties and methods.
	 * This class provides a default constructor to create a ServiceCommand object without any arguments.
	 * <p>
	 * Example usage:
	 * ServiceCommand command = new ServiceCommand();
	 */
	public ServiceCommand() {
		super();
	}
}
