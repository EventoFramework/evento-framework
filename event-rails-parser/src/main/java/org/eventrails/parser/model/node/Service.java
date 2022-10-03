package org.eventrails.parser.model.node;

import org.eventrails.parser.model.handler.ServiceCommandHandler;

import java.util.List;

public class Service extends Node {
	private List<ServiceCommandHandler> commandHandlers;

	public void setCommandHandlers(List<ServiceCommandHandler> commandHandlers) {
		this.commandHandlers = commandHandlers;
	}

	public List<ServiceCommandHandler> getCommandHandlers() {
		return commandHandlers;
	}
}
