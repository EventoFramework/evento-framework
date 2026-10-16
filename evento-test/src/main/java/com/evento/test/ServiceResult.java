package com.evento.test;

import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.messaging.payload.ServiceEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * What a service command produced in a {@link ServiceFixture}: the event (null when the handler
 * returns nothing), or the exception, and the commands the handler sent.
 */
public final class ServiceResult {

    private final ServiceEvent event;
    private final Throwable exception;
    private final List<Command> commandsSent;

    ServiceResult(ServiceEvent event, Throwable exception, List<Command> commandsSent) {
        this.event = event;
        this.exception = exception;
        this.commandsSent = commandsSent;
    }

    /** The event the handler returned, or null. */
    public ServiceEvent event() {
        return event;
    }

    /** The exception the handler threw, or null. */
    public Throwable exception() {
        return exception;
    }

    /** The commands the handler sent, in order. */
    public List<Command> commandsSent() {
        return commandsSent;
    }

    /** The handler returned an event equal to {@code expected} (by {@code equals}, else field by field). */
    public ServiceResult expectEvent(ServiceEvent expected) {
        requireSuccess();
        if (!Payloads.same(expected, event)) {
            throw new AssertionError("expected event " + Payloads.describe(expected) + " but was " + Payloads.describe(event));
        }
        return this;
    }

    /** The handler returned an event of {@code type} that passes {@code check}. */
    public <E extends ServiceEvent> ServiceResult expectEvent(Class<E> type, Consumer<E> check) {
        requireSuccess();
        if (!type.isInstance(event)) {
            throw new AssertionError("expected a " + type.getSimpleName() + " but was " + Payloads.describe(event));
        }
        check.accept(type.cast(event));
        return this;
    }

    /** The handler completed without an event. */
    public ServiceResult expectNoEvent() {
        requireSuccess();
        if (event != null) {
            throw new AssertionError("expected no event but was " + Payloads.describe(event));
        }
        return this;
    }

    /** The handler threw an exception of {@code type} (or a subtype). */
    public ServiceResult expectException(Class<? extends Throwable> type) {
        if (exception == null) {
            throw new AssertionError("expected " + type.getSimpleName() + " but the command produced "
                    + Payloads.describe(event));
        }
        if (!type.isInstance(exception)) {
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + exception, exception);
        }
        return this;
    }

    /** The handler sent exactly these commands, in this order. */
    public ServiceResult expectCommandsSent(Command... expected) {
        requireSuccess();
        Expectations.sameCommands(List.of(expected), commandsSent);
        return this;
    }

    private void requireSuccess() {
        if (exception != null) {
            throw new AssertionError("expected the command to succeed but it failed with " + exception, exception);
        }
    }
}
