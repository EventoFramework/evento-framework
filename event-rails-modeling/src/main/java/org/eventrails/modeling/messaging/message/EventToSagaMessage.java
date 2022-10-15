package org.eventrails.modeling.messaging.message;

import org.eventrails.modeling.state.SerializedSagaState;

import java.io.Serializable;

public class EventToSagaMessage implements Serializable {

	private EventMessage<?> eventMessage;

	private SerializedSagaState<?> serializedSagaState;
	private String sagaName;


	public EventToSagaMessage(EventMessage<?> eventMessage, SerializedSagaState<?> serializedSagaState, String sagaName) {
		this.eventMessage = eventMessage;
		this.serializedSagaState = serializedSagaState;
		this.sagaName = sagaName;
	}

	public EventToSagaMessage() {
	}

	public EventMessage<?> getEventMessage() {
		return eventMessage;
	}

	public void setEventMessage(EventMessage<?> eventMessage) {
		this.eventMessage = eventMessage;
	}

	public SerializedSagaState getSerializedSagaState() {
		return serializedSagaState;
	}

	public void setSerializedSagaState(SerializedSagaState serializedSagaState) {
		this.serializedSagaState = serializedSagaState;
	}

	public String getSagaName() {
		return sagaName;
	}

	public void setSagaName(String sagaName) {
		this.sagaName = sagaName;
	}
}
