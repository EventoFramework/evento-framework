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

    /**
     * Whether this consumer has a handler for {@code event} (default: assume it does). The
     * consume loop still hands it every event; it only uses this to write the checkpoint of an
     * event nobody here handles together with the next one, not on its own: with seventeen
     * projectors in one bundle every event of the stream was seventeen state-store writes,
     * most of them for events none of them handles.
     */
    default boolean handles(PublishedEvent event) {
        return true;
    }

    /** A consumer that {@link #handles} only the events of {@code eventNames}. */
    static EventConsumer of(EventConsumer consumer, java.util.Set<String> eventNames) {
        return new EventConsumer() {
            @Override
            public void consume(PublishedEvent event) throws Throwable {
                consumer.consume(event);
            }

            @Override
            public boolean handles(PublishedEvent event) {
                return eventNames.contains(event.getEventName());
            }
        };
    }
}
