package org.eventrails.parser.model.node;

import org.eventrails.parser.model.handler.SagaEventHandler;

import java.util.List;

public class Saga extends Node {

	public List<SagaEventHandler> sagaEventHandlers;

	public List<SagaEventHandler> getSagaEventHandlers() {
		return sagaEventHandlers;
	}

	public void setSagaEventHandlers(List<SagaEventHandler> sagaEventHandlers) {
		this.sagaEventHandlers = sagaEventHandlers;
	}
}
