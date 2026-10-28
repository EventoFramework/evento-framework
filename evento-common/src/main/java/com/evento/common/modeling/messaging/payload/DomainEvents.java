package com.evento.common.modeling.messaging.payload;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Several events from one aggregate command (3.0), returned by an
 * {@code @AggregateCommandHandler} in place of a single {@link DomainEvent}:
 *
 * <pre>{@code
 * @AggregateCommandHandler(produces = {OrderPlaced.class, DiscountApplied.class})
 * DomainEvents handle(PlaceOrder command, OrderState state) {
 *     return DomainEvents.of(new OrderPlaced(...), new DiscountApplied(...));
 * }
 * }</pre>
 *
 * <p>The events are applied to the aggregate in order (each through its
 * {@code @EventSourcingHandler}) and stored atomically: all of them, consecutive in the event
 * store and in the aggregate's versions, or none. The command's caller is answered with the last
 * one. Consumers see them as any other events, one by one.
 *
 * <p>The handler declares the events it may produce in {@code produces}, so the catalog knows
 * them without running it; an event outside {@code produces} fails the command (3.0.1), so the
 * catalog's interaction graph stays exact. It needs brokers that store several events per command (3.0): a bundle
 * connected to an older broker refuses to answer with more than one, rather than have the broker
 * store only one.
 */
public final class DomainEvents implements Iterable<DomainEvent> {

    private final List<DomainEvent> events;

    private DomainEvents(List<DomainEvent> events) {
        this.events = events;
    }

    /** The events, in the order they happened; at least one, no nulls. */
    public static DomainEvents of(DomainEvent... events) {
        return of(List.of(events));
    }

    /** The events, in the order they happened; at least one, no nulls. */
    public static DomainEvents of(List<? extends DomainEvent> events) {
        Objects.requireNonNull(events, "events");
        if (events.isEmpty()) throw new IllegalArgumentException("a command handler must return at least one event");
        return new DomainEvents(List.copyOf(events));
    }

    /** The events, in order. */
    public List<DomainEvent> events() {
        return events;
    }

    /** How many events. */
    public int size() {
        return events.size();
    }

    @Override
    public Iterator<DomainEvent> iterator() {
        return events.iterator();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DomainEvents other && events.equals(other.events);
    }

    @Override
    public int hashCode() {
        return events.hashCode();
    }

    @Override
    public String toString() {
        return "DomainEvents" + events;
    }
}
