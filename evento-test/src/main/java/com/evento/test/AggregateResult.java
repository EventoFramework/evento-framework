package com.evento.test;

import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.state.AggregateState;

import java.util.List;
import java.util.function.Consumer;

/**
 * What an aggregate command produced in an {@link AggregateFixture}: the event, the state after
 * it, or the exception. Every {@code expect} method throws an {@link AssertionError} on mismatch
 * and returns this result for chaining; the accessors give the raw values for other assertion
 * libraries.
 */
public final class AggregateResult {

    private final List<DomainEvent> events;
    private final DomainEvent event;
    private final AggregateState state;
    private final Throwable exception;
    private final List<Command> commandsSent;

    AggregateResult(List<DomainEvent> events, AggregateState state, Throwable exception, List<Command> commandsSent) {
        this.events = List.copyOf(events);
        this.event = events.isEmpty() ? null : events.getLast();
        this.state = state;
        this.exception = exception;
        this.commandsSent = commandsSent;
    }

    /**
     * The event the command handler returned, or null when it threw. For a handler that returned
     * {@code DomainEvents}, the last one, the event its caller is answered with.
     */
    public DomainEvent event() {
        return event;
    }

    /** Every event the command handler returned, in order (empty when it threw). */
    public List<DomainEvent> events() {
        return events;
    }

    /** The state after the event (or as rebuilt from the history, when the handler threw). */
    public AggregateState state() {
        return state;
    }

    /** The exception the handling threw, or null. */
    public Throwable exception() {
        return exception;
    }

    /** Commands the handler sent through its gateway. */
    public List<Command> commandsSent() {
        return commandsSent;
    }

    /**
     * The handler returned exactly these events, in this order, each equal (by {@code equals},
     * else field by field) to the one expected: for a handler that returns {@code DomainEvents}.
     */
    public AggregateResult expectEvents(DomainEvent... expected) {
        requireEvent();
        boolean same = expected.length == events.size();
        for (int i = 0; same && i < expected.length; i++) same = Payloads.same(expected[i], events.get(i));
        if (!same) {
            throw new AssertionError("expected events " + describe(List.of(expected)) + " but were " + describe(events));
        }
        return this;
    }

    /** The handler returned an event equal to {@code expected} (by {@code equals}, else field by field). */
    public AggregateResult expectEvent(DomainEvent expected) {
        requireEvent();
        requireOneEvent();
        if (!Payloads.same(expected, event)) {
            throw new AssertionError("expected event " + Payloads.describe(expected) + " but was " + Payloads.describe(event));
        }
        return this;
    }

    /** The handler returned an event of {@code type} that passes {@code check}. */
    public <E extends DomainEvent> AggregateResult expectEvent(Class<E> type, Consumer<E> check) {
        requireEvent();
        requireOneEvent();
        if (!type.isInstance(event)) {
            throw new AssertionError("expected a " + type.getSimpleName() + " but was " + Payloads.describe(event));
        }
        check.accept(type.cast(event));
        return this;
    }

    /** The handling threw an exception of {@code type} (or a subtype). */
    public AggregateResult expectException(Class<? extends Throwable> type) {
        return expectException(type, e -> {
        });
    }

    /** The handling threw an exception of {@code type} that passes {@code check}. */
    public <T extends Throwable> AggregateResult expectException(Class<T> type, Consumer<T> check) {
        if (exception == null) {
            throw new AssertionError("expected " + type.getSimpleName() + " but the command produced "
                    + Payloads.describe(event));
        }
        if (!type.isInstance(exception)) {
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + exception, exception);
        }
        check.accept(type.cast(exception));
        return this;
    }

    /** The state after the event is a {@code type} that passes {@code check}. */
    public <S extends AggregateState> AggregateResult expectState(Class<S> type, Consumer<S> check) {
        requireEvent();
        if (!type.isInstance(state)) {
            throw new AssertionError("expected state " + type.getSimpleName() + " but was " + Payloads.describe(state));
        }
        check.accept(type.cast(state));
        return this;
    }

    /** The event marked the aggregate as deleted. */
    public AggregateResult expectDeleted() {
        requireEvent();
        if (state == null || !state.isDeleted()) {
            throw new AssertionError("expected the aggregate to be deleted, state: " + Payloads.describe(state));
        }
        return this;
    }

    /** The handler sent exactly these commands, in this order. */
    public AggregateResult expectCommandsSent(Command... expected) {
        Expectations.sameCommands(List.of(expected), commandsSent);
        return this;
    }

    private void requireOneEvent() {
        if (events.size() != 1) {
            throw new AssertionError("the command produced " + events.size() + " events, " + describe(events)
                    + ": check them with expectEvents");
        }
    }

    private static String describe(List<? extends DomainEvent> list) {
        return list.stream().map(Payloads::describe).toList().toString();
    }

    private void requireEvent() {
        if (exception != null) {
            throw new AssertionError("expected an event but the command failed with " + exception, exception);
        }
    }
}
