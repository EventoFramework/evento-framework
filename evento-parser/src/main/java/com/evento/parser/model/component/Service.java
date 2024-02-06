package com.evento.parser.model.component;

import com.evento.parser.model.handler.ServiceCommandHandler;

import java.util.List;

/**
 * The Service class represents a service component.
 * It extends the Component class and provides additional functionality specific to service components.
 * <p>
 * The service component contains a list of service command handlers.
 */
public class Service extends Component {
	private List<ServiceCommandHandler> commandHandlers;

	/**
	 * Retrieves the list of command handlers associated with the service component.
	 *
	 * @return A List of ServiceCommandHandler objects representing the command handlers.
	 */
	public List<ServiceCommandHandler> getCommandHandlers() {
		return commandHandlers;
	}

	/**
	 * Sets the command handlers associated with the service component.
	 *
	 * @param commandHandlers A List of ServiceCommandHandler objects representing the command handlers.
	 */
	public void setCommandHandlers(List<ServiceCommandHandler> commandHandlers) {
		this.commandHandlers = commandHandlers;
	}
}
