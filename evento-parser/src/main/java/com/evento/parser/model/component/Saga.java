package com.evento.parser.model.component;

import com.evento.parser.model.handler.SagaEventHandler;

import java.util.List;

/**
 * The Saga class represents a saga component. It extends the Component class and provides additional properties and methods specific to sagas.
 */
public class Saga extends Component {

	private List<SagaEventHandler> sagaEventHandlers;

	/**
	 * Retrieves the list of saga event handlers.
	 *
	 * @return A List of SagaEventHandler objects representing the saga event handlers.
	 */
	public List<SagaEventHandler> getSagaEventHandlers() {
		return sagaEventHandlers;
	}

	/**
	 * Sets the list of saga event handlers for the Saga object.
	 *
	 * @param sagaEventHandlers The list of SagaEventHandler objects representing the saga event handlers to be set.
	 */
	public void setSagaEventHandlers(List<SagaEventHandler> sagaEventHandlers) {
		this.sagaEventHandlers = sagaEventHandlers;
	}
}
