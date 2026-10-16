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

    private final DomainEvent event;
    private final AggregateState state;
    private final Throwable exception;
    private final List<Command> commandsSent;

    AggregateResult(DomainEvent event, AggregateState state, Throwable exception, List<Command> commandsSent) {
        this.event = event;
        this.state = state;
        this.exception = exception;
        this.commandsSent = commandsSent;
    }

    /** The event the command handler returned, or null when it threw. */
    public DomainEvent event() {
        return event;
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

    /** The handler returned an event equal to {@code expected} (by {@code equals}, else field by field). */
    public AggregateResult expectEvent(DomainEvent expected) {
        requireEvent();
        if (!Payloads.same(expected, event)) {
            throw new AssertionError("expected event " + Payloads.describe(expected) + " but was " + Payloads.describe(event));
        }
        return this;
    }

    /** The handler returned an event of {@code type} that passes {@code check}. */
    public <E extends DomainEvent> AggregateResult expectEvent(Class<E> type, Consumer<E> check) {
        requireEvent();
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

    private void requireEvent() {
        if (exception != null) {
            throw new AssertionError("expected an event but the command failed with " + exception, exception);
        }
    }
}
