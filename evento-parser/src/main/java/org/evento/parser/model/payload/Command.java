package org.evento.parser.model.payload;

/**
 * The Command class represents a command payload. It extends the Payload class and inherits its properties and methods.
 * Contains two constructors to create a Command object with a specified name or without a name.
 */
public class Command extends Payload {
	/**
	 * The Command class represents a command payload. It extends the Payload class and inherits its properties and methods.
     * @param name the command name
     */
	public Command(String name) {
		super(name);
	}

	/**
	 * The Command class represents a command payload.
	 * It extends the Payload class and inherits its properties and methods.
	 */
	public Command() {
		super();
	}
}
