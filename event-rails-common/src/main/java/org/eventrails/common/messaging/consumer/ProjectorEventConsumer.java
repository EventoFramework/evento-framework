package org.eventrails.common.messaging.consumer;

import org.eventrails.common.modeling.messaging.dto.PublishedEvent;

public interface ProjectorEventConsumer {

    public void consume(PublishedEvent event) throws Throwable;
}
