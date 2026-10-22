package com.evento.test;

import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.state.SagaState;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * What a saga did with an event in a {@link SagaFixture}: the commands it sent, its state after
 * the event, or the exception. Every {@code expect} method throws an {@link AssertionError} on
 * mismatch and returns this result for chaining.
 */
public final class SagaResult {

    private final SagaState state;
    private final Throwable exception;
    private final List<Command> commandsSent;

    SagaResult(SagaState state, Throwable exception, List<Command> commandsSent) {
        this.state = state;
        this.exception = exception;
        this.commandsSent = commandsSent;
    }

    /** The state after the event (unchanged when the handler threw). */
    public SagaState state() {
        return state;
    }

    /** The exception the handler threw, or null. */
    public Throwable exception() {
        return exception;
    }

    /** The commands the handler sent, in order. */
    public List<Command> commandsSent() {
        return commandsSent;
    }

    /** The handler sent exactly these commands, in this order. */
    public SagaResult expectCommands(Command... expected) {
        requireSuccess();
        Expectations.sameCommands(List.of(expected), commandsSent);
        return this;
    }

    /** Among the commands sent there is one of {@code type} that passes {@code check}. */
    public <C extends Command> SagaResult expectCommand(Class<C> type, Consumer<C> check) {
        requireSuccess();
        var candidates = commandsSent.stream().filter(type::isInstance).map(type::cast).toList();
        if (candidates.isEmpty()) {
            throw new AssertionError("expected a " + type.getSimpleName() + " among "
                    + Expectations.describe(commandsSent));
        }
        AssertionError last = null;
        for (var c : candidates) {
            try {
                check.accept(c);
                return this;
            } catch (AssertionError e) {
                last = e;
            }
        }
        throw last;
    }

    /** The handler sent no command. */
    public SagaResult expectNoCommands() {
        requireSuccess();
        if (!commandsSent.isEmpty()) {
            throw new AssertionError("expected no commands but were " + Expectations.describe(commandsSent));
        }
        return this;
    }

    /** The saga ended with this event. */
    public SagaResult expectEnded() {
        requireSuccess();
        if (state == null || !state.isEnded()) {
            throw new AssertionError("expected the saga to end, state: " + Payloads.describe(state));
        }
        return this;
    }

    /** The saga is still running after this event. */
    public SagaResult expectNotEnded() {
        requireSuccess();
        if (state != null && state.isEnded()) {
            throw new AssertionError("expected the saga to go on, but it ended");
        }
        return this;
    }

    /** The saga is associated with {@code value} on {@code property}. */
    public SagaResult expectAssociation(String property, String value) {
        requireSuccess();
        var actual = state == null ? null : state.getAssociation(property);
        if (!Objects.equals(value, actual)) {
            throw new AssertionError("expected association " + property + "=" + value + " but was " + actual);
        }
        return this;
    }

    /** The state after the event is a {@code type} that passes {@code check}. */
    public <T extends SagaState> SagaResult expectState(Class<T> type, Consumer<T> check) {
        requireSuccess();
        if (!type.isInstance(state)) {
            throw new AssertionError("expected state " + type.getSimpleName() + " but was " + Payloads.describe(state));
        }
        check.accept(type.cast(state));
        return this;
    }

    /** The handler threw an exception of {@code type} (or a subtype). */
    public SagaResult expectException(Class<? extends Throwable> type) {
        if (exception == null) {
            throw new AssertionError("expected " + type.getSimpleName() + " but the saga handled the event");
        }
        if (!type.isInstance(exception)) {
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + exception, exception);
        }
        return this;
    }

    private void requireSuccess() {
        if (exception != null) {
            throw new AssertionError("the saga failed with " + exception, exception);
        }
    }
}
