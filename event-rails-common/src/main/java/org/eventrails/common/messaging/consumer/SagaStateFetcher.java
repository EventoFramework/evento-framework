package org.eventrails.common.messaging.consumer;

import org.eventrails.common.modeling.state.SagaState;

public interface SagaStateFetcher {
    SagaState getLastState(String sagaName, String associationProperty, String associationValue) throws Exception;
}
