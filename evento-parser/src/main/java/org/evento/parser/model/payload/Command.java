package org.evento.parser.model.payload;

/**
 * The Command class represents a command payload. It extends the Payload class and inherits its properties and methods.
 * <p>
 * Usage example:
 * <p>
 * Command usage example:
 * <p>
 * public class ServiceCommand extends Command {
 *     public ServiceCommand(String name) {
 *         super(name);
 *     }
 * <p>
 *     public ServiceCommand() {
 *         super();
 *     }
 * }
 * <p>
 * Command usage example:
 * <p>
 * public class DomainCommand extends Command {
 *     public DomainCommand(String name) {
 *         super(name);
 *     }
 * <p>
 *     public DomainCommand() {
 *         super();
 *     }
 * }
 * <p>
 * Payload declaration:
 * <p>
 * public class Payload implements Serializable {
 *     private String name;
 *     private String domain;
 * <p>
 *     public Payload(String name) {
 *         this.name = name;
 *     }
 * <p>
 *     public Payload() {
 *     }
 * <p>
 *     public String getName() {
 *         return name;
 *     }
 * <p>
 *     public void setName(String name) {
 *         this.name = name;
 *     }
 * <p>
 *     public String getDomain() {
 *         return domain;
 *     }
 * <p>
 *     public void setDomain(String domain) {
 *         this.domain = domain;
 *     }
 * }
 * <p> <p>
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
