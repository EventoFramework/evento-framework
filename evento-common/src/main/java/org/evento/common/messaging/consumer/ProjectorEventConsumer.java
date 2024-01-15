package org.evento.common.messaging.consumer;

import org.evento.common.modeling.messaging.dto.PublishedEvent;

/**
 * Interface for consuming projector events.
 */
public interface ProjectorEventConsumer {

	/**
	 * Consumes a published event.
	 *
	 * @param event the event to consume
	 * @throws Throwable if an error occurs during consumption
	 */
	public void consume(PublishedEvent event) throws Throwable;
}
