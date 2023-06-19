package org.evento.common.messaging.consumer;

import org.evento.common.modeling.messaging.dto.PublishedEvent;

public interface ProjectorEventConsumer {

	public void consume(PublishedEvent event) throws Throwable;
}
