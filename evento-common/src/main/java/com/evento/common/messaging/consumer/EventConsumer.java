package com.evento.common.messaging.consumer;

import com.evento.common.modeling.messaging.dto.PublishedEvent;


/**
 * Represents an event consumer that consumes published events.
 */
public interface EventConsumer {

	/**
	 * Consumes a published event.
	 *
	 * @param event the event to consume
	 * @throws Throwable if an error occurs during consumption
	 */
    void consume(PublishedEvent event) throws Throwable;
}
