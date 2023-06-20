package org.evento.common.messaging.consumer;

import org.evento.common.modeling.state.SagaState;

public interface SagaStateFetcher {
	SagaState getLastState(String sagaName, String associationProperty, String associationValue) throws Exception;
}
