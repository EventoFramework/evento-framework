package com.evento.common.messaging.consumer;

import com.evento.common.modeling.state.SagaState;

/**
 * The SagaStateFetcher interface is responsible for retrieving the last state of a saga.
 */
public interface SagaStateFetcher {
	/**
	 * Retrieves the last state of a saga.
	 *
	 * @param sagaName The name of the saga.
	 * @param associationProperty The property used for association.
	 * @param associationValue The value of the association property.
	 * @return The last state of the saga.
	 * @throws Exception if an error occurs while retrieving the last state.
	 */
	SagaState getLastState(String sagaName, String associationProperty, String associationValue) throws Exception;
}
