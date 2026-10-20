package com.evento.test;

import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.application.reference.AggregateReference;
import com.evento.application.reference.AggregateStateEnvelope;
import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.messaging.message.application.DomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.payload.DomainCommand;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.state.AggregateState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Given/when/then test of an {@link Aggregate}: the aggregate's own handlers run exactly as in a
 * bundle (the {@code @EventSourcingHandler}s rebuild the state from the given events, the
 * {@code @AggregateCommandHandler} decides), with no broker, event store or database.
 *
 * <pre>{@code
 * AggregateFixture.of(new OrderAggregate())
 *     .given(new OrderCreatedEvent("o1", 2))
 *     .when(new AddItemCommand("o1", "pen"))
 *     .expectEvent(new ItemAddedEvent("o1", "pen"))
 *     .expectState(OrderState.class, s -> assertEquals(1, s.getItems().size()));
 * }</pre>
 *
 * <p>Commands and queries a handler sends go to {@link #commandGateway()} (recorded) and
 * {@link #queryGateway()} (answered by stubs).
 *
 * @param <A> the aggregate class
 */
public final class AggregateFixture<A> {

    private final AggregateReference reference;
    private final RecordingCommandGateway commandGateway = new RecordingCommandGateway();
    private final StubQueryGateway queryGateway = new StubQueryGateway();
    private final List<DomainEvent> history = new ArrayList<>();
    private AggregateState snapshot;

    private AggregateFixture(A aggregate) {
        if (aggregate.getClass().getAnnotation(Aggregate.class) == null) {
            throw new IllegalArgumentException(aggregate.getClass().getName() + " is not annotated with @Aggregate");
        }
        this.reference = new AggregateReference(aggregate, -1);
    }

    /** A fixture around an instance of the aggregate, built as the bundle would (any injected collaborators included). */
    public static <A> AggregateFixture<A> of(A aggregate) {
        return new AggregateFixture<>(aggregate);
    }

    /** Commands the handlers send, recorded; stub their results with {@code answer}. */
    public RecordingCommandGateway commandGateway() {
        return commandGateway;
    }

    /** Queries the handlers ask, answered by the stubs registered with {@code answer}. */
    public StubQueryGateway queryGateway() {
        return queryGateway;
    }

    /** The aggregate's history before the command, oldest first. Cumulative. */
    public AggregateFixture<A> given(DomainEvent... events) {
        history.addAll(Arrays.asList(events));
        return this;
    }

    /** No history: the command must be the aggregate's initializer ({@code init = true}). */
    public AggregateFixture<A> givenNoPriorActivity() {
        history.clear();
        snapshot = null;
        return this;
    }

    /** Start from a snapshot of the state; {@link #given} events apply after it. */
    public AggregateFixture<A> givenState(AggregateState state) {
        this.snapshot = state;
        return this;
    }

    /** Handle {@code command} against the given history and return what happened. */
    public AggregateResult when(DomainCommand command) {
        // The names the framework routes by (the class name after the last dot), so a nested
        // payload class fails here exactly as it would in a bundle.
        var message = new DomainCommandMessage(command);
        var commandName = message.getCommandName();
        if (reference.getAggregateCommandHandler(commandName) == null) {
            throw new AssertionError(reference.getComponentName() + " has no @AggregateCommandHandler for " + commandName);
        }
        for (var event : history) {
            var eventName = new DomainEventMessage(event).getEventName();
            if (reference.getEventSourcingHandler(eventName) == null) {
                throw new AssertionError(reference.getComponentName() + " has no @EventSourcingHandler for the given "
                        + eventName);
            }
        }
        commandGateway.clear();
        var envelope = new AggregateStateEnvelope(snapshot);
        var stream = history.stream().map(DomainEventMessage::new).toList();
        try {
            var events = reference.invokeAll(message, envelope, stream,
                    commandGateway, queryGateway, new MessageHandlerInterceptor() {
                    });
            return new AggregateResult(events, envelope.getAggregateState(), null, commandGateway.sent());
        } catch (Throwable t) {
            return new AggregateResult(List.of(), envelope.getAggregateState(), t, commandGateway.sent());
        }
    }
}
