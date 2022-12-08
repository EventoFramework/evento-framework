package org.evento.application.reference;

import org.evento.common.modeling.state.AggregateState;

public class AggregateStateEnvelope {
    private AggregateState aggregateState;

    public AggregateStateEnvelope(AggregateState aggregateState) {
        this.aggregateState = aggregateState;
    }

    public AggregateStateEnvelope() {
    }

    public AggregateState getAggregateState() {
        return aggregateState;
    }

    public void setAggregateState(AggregateState aggregateState) {
        this.aggregateState = aggregateState;
    }
}
