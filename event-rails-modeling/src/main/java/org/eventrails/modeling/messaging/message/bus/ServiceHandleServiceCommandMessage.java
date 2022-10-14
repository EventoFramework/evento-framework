package org.eventrails.modeling.messaging.message.bus;

public class ServiceHandleServiceCommandMessage extends InvocationMessage {
	private String commandName;

	public ServiceHandleServiceCommandMessage(String commandName, String payload) {
		this.commandName = commandName;
		this.payload = payload;
	}

	public ServiceHandleServiceCommandMessage() {

	}

	public String getCommandName() {
		return commandName;
	}

	public void setCommandName(String commandName) {
		this.commandName = commandName;
	}
}
