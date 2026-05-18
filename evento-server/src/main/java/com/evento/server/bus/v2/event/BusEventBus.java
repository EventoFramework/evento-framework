package com.evento.server.bus.v2.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Single publisher / many subscribers for {@link BusEvent}s. Subscribers are held
 * in a {@code CopyOnWriteArrayList}, so iteration during {@code publish} never
 * blocks new registrations. Subscriber callbacks run synchronously on the
 * publisher's thread; if a callback throws, the failure is logged and the
 * remaining subscribers still receive the event.
 *
 * <p>Compared to v1's four parallel listener lists with mixed
 * {@code synchronized} blocks (deadlock-prone when callbacks themselves touch
 * the bus), this is one type, one iteration pattern, no monitor entry on the
 * hot path.
 */
public final class BusEventBus {

    private static final Logger log = LoggerFactory.getLogger(BusEventBus.class);

    private final CopyOnWriteArrayList<Consumer<BusEvent>> subscribers = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<BusEvent> subscriber) {
        subscribers.add(Objects.requireNonNull(subscriber, "subscriber"));
    }

    public void unsubscribe(Consumer<BusEvent> subscriber) {
        subscribers.remove(subscriber);
    }

    public void publish(BusEvent event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<BusEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Throwable t) {
                log.error("event=subscriber_error type={} subscriber={}",
                        event.getClass().getSimpleName(), subscriber, t);
            }
        }
    }

    public int subscriberCount() {
        return subscribers.size();
    }
}
