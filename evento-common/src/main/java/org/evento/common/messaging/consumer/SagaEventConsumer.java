package org.evento.common.messaging.consumer;

import org.evento.common.modeling.messaging.dto.PublishedEvent;
import org.evento.common.modeling.state.SagaState;

/**
 * The SagaEventConsumer interface represents a consumer for saga events.
 */
public interface SagaEventConsumer {

	/**
	 * Consumes a published event for a saga and returns the updated saga state.
	 *
	 * @param sagaStateFetcher  the saga state fetcher used to retrieve the last state of the saga
	 * @param event  the published event to be consumed
	 * @return the updated saga state
	 * @throws Throwable if an error occurs during consumption
	 */
    SagaState consume(SagaStateFetcher sagaStateFetcher, PublishedEvent event) throws Throwable;
}
