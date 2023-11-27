package org.evento.application.reference;

import lombok.Getter;
import lombok.Setter;
import org.evento.common.modeling.state.AggregateState;

/**
 * Class representing an envelope for an aggregate state.
 */
@Getter
@Setter
public class AggregateStateEnvelope {
    private AggregateState aggregateState;

    public AggregateStateEnvelope(AggregateState aggregateState) {
        this.aggregateState = aggregateState;
    }
}
