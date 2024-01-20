package org.evento.common.messaging.consumer;

import org.evento.common.modeling.messaging.dto.PublishedEvent;


/**
 * Represents an event consumer that consumes published events.
 */
public interface EventConsumer {

	/**
	 * Consumes a published event.
	 *
	 * @param event the event to consume
	 * @throws Exception if an error occurs during consumption
	 */
    void consume(PublishedEvent event) throws Exception;
}
