package org.eventrails.modeling.messaging.message.bus;

public class ServiceHandleDomainCommandMessage extends InvocationMessage {
	private String commandName;

	public ServiceHandleDomainCommandMessage(String commandName, String payload) {
		this.commandName = commandName;
		this.payload = payload;
	}

	public ServiceHandleDomainCommandMessage() {

	}

	public String getCommandName() {
		return commandName;
	}

	public void setCommandName(String commandName) {
		this.commandName = commandName;
	}
}
