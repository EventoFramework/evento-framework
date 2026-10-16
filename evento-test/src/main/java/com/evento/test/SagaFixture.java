package com.evento.test;

import com.evento.application.reference.SagaReference;
import com.evento.application.utils.ReflectionUtils;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.messaging.message.application.ServiceEventMessage;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.messaging.payload.Event;
import com.evento.common.modeling.messaging.payload.ServiceEvent;
import com.evento.common.modeling.state.SagaState;

import java.time.Instant;

/**
 * Given/when/then test of a {@link Saga}: its {@code @SagaEventHandler}s run as in a bundle, one
 * saga instance at a time, with the commands they send recorded instead of delivered.
 *
 * <pre>{@code
 * SagaFixture.of(new PaymentSaga())
 *     .given(new OrderCreatedEvent("o1", 30))
 *     .whenEvent(new OrderConfirmedEvent("o1"))
 *     .expectCommands(new ChargeCommand("o1", 30))
 *     .expectNotEnded();
 * }</pre>
 *
 * <p>The given events build the saga state; the commands they send are not part of the result.
 * The framework's retries and dead-letter handling are not involved: a handler that throws shows
 * up in {@link SagaResult#exception()}.
 *
 * @param <S> the saga class
 */
public final class SagaFixture<S> {

    private final SagaReference reference;
    private final RecordingCommandGateway commandGateway = new RecordingCommandGateway();
    private final StubQueryGateway queryGateway = new StubQueryGateway();
    private SagaState state;
    private long sequence;

    private SagaFixture(S saga) {
        if (saga.getClass().getAnnotation(Saga.class) == null) {
            throw new IllegalArgumentException(saga.getClass().getName() + " is not annotated with @Saga");
        }
        this.reference = new SagaReference(saga);
    }

    /** A fixture around an instance of the saga. */
    public static <S> SagaFixture<S> of(S saga) {
        return new SagaFixture<>(saga);
    }

    /** Commands the handlers send, recorded; stub their results with {@code answer}. */
    public RecordingCommandGateway commandGateway() {
        return commandGateway;
    }

    /** Queries the handlers ask, answered by the stubs registered with {@code answer}. */
    public StubQueryGateway queryGateway() {
        return queryGateway;
    }

    /** Start from this state instead of a fresh saga. */
    public SagaFixture<S> givenState(SagaState state) {
        this.state = state;
        return this;
    }

    /** Events the saga already handled, oldest first. Fails at the first handler that throws. */
    public SagaFixture<S> given(Event... events) {
        for (var event : events) {
            try {
                handle(event);
            } catch (Throwable t) {
                throw new AssertionError("the saga failed on the given " + Payloads.describe(event), t);
            }
        }
        return this;
    }

    /** Handle {@code event} and return the commands it sent and the state after it. */
    public SagaResult whenEvent(Event event) {
        commandGateway.clear();
        try {
            handle(event);
            return new SagaResult(state, null, commandGateway.sent());
        } catch (Throwable t) {
            return new SagaResult(state, t, commandGateway.sent());
        }
    }

    private void handle(Event event) throws Throwable {
        var published = publish(event);
        var message = published.getEventMessage();
        var handler = reference.getSagaEventHandler(published.getEventName());
        if (handler == null) {
            throw new AssertionError(reference.getComponentName() + " has no @SagaEventHandler for "
                    + published.getEventName());
        }
        var next = (SagaState) ReflectionUtils.invoke(reference.getRef(), handler,
                event, state, commandGateway, queryGateway, published, message.getMetadata(),
                Instant.ofEpochMilli(message.getTimestamp()));
        if (next != null) state = next;
    }

    private PublishedEvent publish(Event event) {
        EventMessage<?> message = switch (event) {
            case DomainEvent e -> new DomainEventMessage(e);
            case ServiceEvent e -> new ServiceEventMessage(e);
            default -> throw new IllegalArgumentException("not a DomainEvent or ServiceEvent: " + event.getClass().getName());
        };
        var published = new PublishedEvent();
        published.setEventMessage(message);
        published.setEventName(message.getEventName());
        published.setEventSequenceNumber(++sequence);
        published.setCreatedAt(message.getTimestamp());
        return published;
    }
}
