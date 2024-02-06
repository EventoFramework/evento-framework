package com.evento.parser.model.component;

import com.evento.parser.model.handler.AggregateCommandHandler;
import com.evento.parser.model.handler.EventSourcingHandler;

import java.util.List;

/**
 * The Aggregate class represents an aggregate component. It extends the Component class.
 * It contains a list of AggregateCommandHandlers and EventSourcingHandlers.
 */
public class Aggregate extends Component {
	private List<AggregateCommandHandler> aggregateCommandHandlers;
	private List<EventSourcingHandler> eventSourcingHandlers;

	/**
	 * Retrieves the list of AggregateCommandHandlers associated with this Aggregate.
	 *
	 * @return The list of AggregateCommandHandlers. Each AggregateCommandHandler represents a command handler for the Aggregate and contains information about the command it handles
	 *, the produced event, and the invoked commands and queries.
	 */
	public List<AggregateCommandHandler> getAggregateCommandHandlers() {
		return aggregateCommandHandlers;
	}

	/**
	 * Sets the list of AggregateCommandHandlers for this Aggregate.
	 *
	 * @param aggregateCommandHandlers The list of AggregateCommandHandlers to set. Each AggregateCommandHandler represents a command handler for the Aggregate and contains information
	 *
	 *                                about the command it handles, the produced event, and the invoked commands and queries.
	 */
	public void setAggregateCommandHandlers(List<AggregateCommandHandler> aggregateCommandHandlers) {
		this.aggregateCommandHandlers = aggregateCommandHandlers;
	}

	/**
	 * Retrieves the list of EventSourcingHandlers associated with this Aggregate.
	 *
	 * @return The list of EventSourcingHandlers. Each EventSourcingHandler represents a handler for the domain events produced by the Aggregate.
	 */
	public List<EventSourcingHandler> getEventSourcingHandlers() {
		return eventSourcingHandlers;
	}

	/**
	 * Sets the list of EventSourcingHandlers for this Aggregate.
	 *
	 * @param eventSourcingHandlers The list of EventSourcingHandlers to set. Each EventSourcingHandler represents a handler for the domain events produced by the Aggregate.
	 */
	public void setEventSourcingHandlers(List<EventSourcingHandler> eventSourcingHandlers) {
		this.eventSourcingHandlers = eventSourcingHandlers;
	}
}
