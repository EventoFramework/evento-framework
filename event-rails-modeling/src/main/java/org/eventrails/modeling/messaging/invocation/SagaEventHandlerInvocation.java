package org.eventrails.modeling.messaging.invocation;

import org.eventrails.modeling.messaging.message.EventMessage;
import org.eventrails.modeling.state.SagaState;

public class SagaEventHandlerInvocation {
	private EventMessage<?> eventMessage;
	private SagaState sagaState;

	public SagaEventHandlerInvocation(EventMessage<?> eventMessage, SagaState sagaState) {
		this.eventMessage = eventMessage;
		this.sagaState = sagaState;
	}

	public SagaEventHandlerInvocation() {
	}

	public EventMessage<?> getEventMessage() {
		return eventMessage;
	}

	public void setEventMessage(EventMessage<?> eventMessage) {
		this.eventMessage = eventMessage;
	}

	public SagaState getSagaState() {
		return sagaState;
	}

	public void setSagaState(SagaState sagaState) {
		this.sagaState = sagaState;
	}
}
