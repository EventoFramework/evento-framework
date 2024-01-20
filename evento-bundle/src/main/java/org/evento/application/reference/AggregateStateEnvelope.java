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

    /**
     * Construct an AggregateStateEnvelope with the given AggregateState.
     *
     * @param aggregateState the AggregateState to be encapsulated in the envelope
     */
    public AggregateStateEnvelope(AggregateState aggregateState) {
        this.aggregateState = aggregateState;
    }
}
