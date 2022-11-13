package org.eventrails.common.modeling.messaging.message.application;

import org.eventrails.common.modeling.state.AggregateState;
import org.eventrails.common.modeling.state.SerializedAggregateState;

import java.io.Serializable;

public class DomainCommandResponseMessage implements Serializable {

    private DomainEventMessage domainEventMessage;
    private SerializedAggregateState<AggregateState> serializedAggregateState;
    public DomainCommandResponseMessage() {

    }

    public DomainCommandResponseMessage(DomainEventMessage domainEventMessage, SerializedAggregateState<AggregateState> serializedAggregateState) {
        this.domainEventMessage = domainEventMessage;
        this.serializedAggregateState = serializedAggregateState;
    }

    public DomainEventMessage getDomainEventMessage() {
        return domainEventMessage;
    }

    public void setDomainEventMessage(DomainEventMessage domainEventMessage) {
        this.domainEventMessage = domainEventMessage;
    }

    public SerializedAggregateState<AggregateState> getSerializedAggregateState() {
        return serializedAggregateState;
    }

    public void setSerializedAggregateState(SerializedAggregateState<AggregateState> serializedAggregateState) {
        this.serializedAggregateState = serializedAggregateState;
    }
}
