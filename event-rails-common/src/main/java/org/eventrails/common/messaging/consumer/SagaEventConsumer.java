package org.eventrails.common.messaging.consumer;

import org.eventrails.common.modeling.messaging.dto.PublishedEvent;
import org.eventrails.common.modeling.state.SagaState;

public interface SagaEventConsumer {

    public SagaState consume(SagaStateFetcher sagaStateFetcher, PublishedEvent event) throws Throwable;
}
