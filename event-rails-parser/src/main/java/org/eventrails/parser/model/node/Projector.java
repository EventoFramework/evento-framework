package org.eventrails.parser.model.node;

import org.eventrails.parser.model.handler.EventHandler;

import java.util.List;

public class Projector extends Node {
	private List<EventHandler> eventHandlers;

	public void setEventHandlers(List<EventHandler> eventHandlers) {
		this.eventHandlers = eventHandlers;
	}

	public List<EventHandler> getEventHandlers() {
		return eventHandlers;
	}
}
