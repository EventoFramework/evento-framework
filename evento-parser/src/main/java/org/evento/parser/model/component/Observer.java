package org.evento.parser.model.component;

import org.evento.parser.model.handler.EventHandler;

import java.util.List;

public class Observer extends Component {
	private List<EventHandler> eventHandlers;

	public void setEventHandlers(List<EventHandler> eventHandlers) {
		this.eventHandlers = eventHandlers;
	}

	public List<EventHandler> getEventHandlers() {
		return eventHandlers;
	}
}
