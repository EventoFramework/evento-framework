package org.evento.common.messaging.consumer;

import org.evento.common.modeling.messaging.dto.PublishedEvent;
import org.evento.common.modeling.state.SagaState;

public interface SagaEventConsumer {

	public SagaState consume(SagaStateFetcher sagaStateFetcher, PublishedEvent event) throws Throwable;
}
