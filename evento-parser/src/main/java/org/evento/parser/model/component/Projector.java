package org.evento.parser.model.component;

import org.evento.parser.model.handler.EventHandler;

import java.util.List;

public class Projector extends Component {
	private List<EventHandler> eventHandlers;

	public List<EventHandler> getEventHandlers() {
		return eventHandlers;
	}

	public void setEventHandlers(List<EventHandler> eventHandlers) {
		this.eventHandlers = eventHandlers;
	}
}
