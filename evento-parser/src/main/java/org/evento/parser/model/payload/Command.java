package org.evento.parser.model.payload;

/**
 * The Command class represents a command payload. It extends the Payload class and inherits its properties and methods.
 *
 * Usage example:
 *
 * Command usage example:
 *
 * public class ServiceCommand extends Command {
 *     public ServiceCommand(String name) {
 *         super(name);
 *     }
 *
 *     public ServiceCommand() {
 *         super();
 *     }
 * }
 *
 * Command usage example:
 *
 * public class DomainCommand extends Command {
 *     public DomainCommand(String name) {
 *         super(name);
 *     }
 *
 *     public DomainCommand() {
 *         super();
 *     }
 * }
 *
 * Payload declaration:
 *
 * public class Payload implements Serializable {
 *     private String name;
 *     private String domain;
 *
 *     public Payload(String name) {
 *         this.name = name;
 *     }
 *
 *     public Payload() {
 *     }
 *
 *     public String getName() {
 *         return name;
 *     }
 *
 *     public void setName(String name) {
 *         this.name = name;
 *     }
 *
 *     public String getDomain() {
 *         return domain;
 *     }
 *
 *     public void setDomain(String domain) {
 *         this.domain = domain;
 *     }
 *
 *     @Override
 *     public int hashCode() {
 *         return name.hashCode();
 *     }
 * }
 *
 * Contains two constructors to create a Command object with a specified name or without a name.
 */
public class Command extends Payload {
	/**
	 * The Command class represents a command payload. It extends the Payload class and inherits its properties and methods.
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
