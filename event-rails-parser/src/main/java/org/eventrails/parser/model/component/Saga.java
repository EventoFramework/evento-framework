package org.eventrails.parser.model.component;

import org.eventrails.parser.model.handler.SagaEventHandler;

import java.util.List;

public class Saga extends Component{

	public List<SagaEventHandler> sagaEventHandlers;

	public List<SagaEventHandler> getSagaEventHandlers() {
		return sagaEventHandlers;
	}

	public void setSagaEventHandlers(List<SagaEventHandler> sagaEventHandlers) {
		this.sagaEventHandlers = sagaEventHandlers;
	}
}
