package com.evento.test;

import com.evento.common.modeling.messaging.payload.Command;

import java.util.List;
import java.util.function.Consumer;

/**
 * What a projector did with an event in a {@link ProjectorFixture}: the exception, if any, and
 * the commands it sent. The projection itself is asserted on the projector's repository.
 */
public final class ConsumerResult {

    private final Throwable exception;
    private final List<Command> commandsSent;

    ConsumerResult(Throwable exception, List<Command> commandsSent) {
        this.exception = exception;
        this.commandsSent = commandsSent;
    }

    /** The exception the handler threw, or null. */
    public Throwable exception() {
        return exception;
    }

    /** The commands the handler sent, in order. */
    public List<Command> commandsSent() {
        return commandsSent;
    }

    /** The handler completed. */
    public ConsumerResult expectSuccess() {
        if (exception != null) {
            throw new AssertionError("the handler failed with " + exception, exception);
        }
        return this;
    }

    /** The handler threw an exception of {@code type} (or a subtype). */
    public ConsumerResult expectException(Class<? extends Throwable> type) {
        return expectException(type, e -> {
        });
    }

    /** The handler threw an exception of {@code type} that passes {@code check}. */
    public <T extends Throwable> ConsumerResult expectException(Class<T> type, Consumer<T> check) {
        if (exception == null) {
            throw new AssertionError("expected " + type.getSimpleName() + " but the handler completed");
        }
        if (!type.isInstance(exception)) {
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + exception, exception);
        }
        check.accept(type.cast(exception));
        return this;
    }

    /** The handler sent exactly these commands, in this order. */
    public ConsumerResult expectCommands(Command... expected) {
        expectSuccess();
        Expectations.sameCommands(List.of(expected), commandsSent);
        return this;
    }

    /** The handler sent no command. */
    public ConsumerResult expectNoCommands() {
        return expectCommands();
    }
}
