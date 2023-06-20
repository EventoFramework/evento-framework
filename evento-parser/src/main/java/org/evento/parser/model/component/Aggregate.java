package org.evento.parser.model.component;

import org.evento.parser.model.handler.AggregateCommandHandler;
import org.evento.parser.model.handler.EventSourcingHandler;

import java.util.List;

public class Aggregate extends Component {
	private List<AggregateCommandHandler> aggregateCommandHandlers;
	private List<EventSourcingHandler> eventSourcingHandlers;

	public List<AggregateCommandHandler> getAggregateCommandHandlers() {
		return aggregateCommandHandlers;
	}

	public void setAggregateCommandHandlers(List<AggregateCommandHandler> aggregateCommandHandlers) {
		this.aggregateCommandHandlers = aggregateCommandHandlers;
	}

	public List<EventSourcingHandler> getEventSourcingHandlers() {
		return eventSourcingHandlers;
	}

	public void setEventSourcingHandlers(List<EventSourcingHandler> eventSourcingHandlers) {
		this.eventSourcingHandlers = eventSourcingHandlers;
	}
}
