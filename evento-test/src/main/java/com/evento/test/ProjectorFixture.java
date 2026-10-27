package com.evento.test;

import com.evento.application.reference.ProjectorReference;
import com.evento.application.utils.ReflectionUtils;
import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.payload.Event;
import com.evento.common.utils.ProjectorStatus;

import java.lang.reflect.Method;
import java.time.Instant;

/**
 * Given/when/then test of a {@link Projector}: its {@code @EventHandler}s run as in a bundle, on
 * the projector instance you build with its real or in-memory repository, and the test asserts
 * on that repository.
 *
 * <pre>{@code
 * var orders = new InMemoryOrderRepository();
 * ProjectorFixture.of(new OrderProjector(orders))
 *     .given(new OrderCreatedEvent("o1"))
 *     .whenEvent(new OrderConfirmedEvent("o1"))
 *     .expectSuccess();
 * assertEquals("CONFIRMED", orders.find("o1").status());
 * }</pre>
 *
 * <p>Events arrive "live" ({@code ProjectorStatus.isHeadReached()} true); {@link #replaying()}
 * makes the handlers see a replay instead. Retries, dead events and checkpoints are the engine's
 * business and stay out: a throwing handler is the result's exception.
 *
 * @param <P> the projector class
 */
public final class ProjectorFixture<P> {

    private final ProjectorReference reference;
    private final RecordingCommandGateway commandGateway = new RecordingCommandGateway();
    private final StubQueryGateway queryGateway = new StubQueryGateway();
    private final ProjectorStatus status = new ProjectorStatus();
    private long sequence;

    private ProjectorFixture(P projector) {
        if (projector.getClass().getAnnotation(Projector.class) == null) {
            throw new IllegalArgumentException(projector.getClass().getName() + " is not annotated with @Projector");
        }
        this.reference = new ProjectorReference(projector);
        this.status.setHeadReached(true);
    }

    /** A fixture around an instance of the projector. */
    public static <P> ProjectorFixture<P> of(P projector) {
        // @PersonalData payloads are written and read with keys in memory, unless the test installed others.
        com.evento.common.personaldata.SubjectKeys.installIfAbsent(
                new com.evento.common.personaldata.InMemorySubjectKeyProvider());
        return new ProjectorFixture<>(projector);
    }

    /** Commands the handlers send, recorded; stub their results with {@code answer}. */
    public RecordingCommandGateway commandGateway() {
        return commandGateway;
    }

    /** Queries the handlers ask, answered by the stubs registered with {@code answer}. */
    public StubQueryGateway queryGateway() {
        return queryGateway;
    }

    /** The handlers see a replay ({@code isHeadReached()} false) from now on. */
    public ProjectorFixture<P> replaying() {
        status.setHeadReached(false);
        return this;
    }

    /** Events the projector already handled, oldest first. Fails at the first handler that throws. */
    public ProjectorFixture<P> given(Event... events) {
        for (var event : events) {
            var published = Events.published(event, ++sequence);
            var handler = handlerFor(published);
            try {
                handle(event, published, handler);
            } catch (Throwable t) {
                throw new AssertionError("the projector failed on the given " + Payloads.describe(event), t);
            }
        }
        return this;
    }

    /** Handle {@code event} and return what happened. */
    public ConsumerResult whenEvent(Event event) {
        var published = Events.published(event, ++sequence);
        var handler = handlerFor(published);
        commandGateway.clear();
        try {
            handle(event, published, handler);
            return new ConsumerResult(null, commandGateway.sent());
        } catch (Throwable t) {
            return new ConsumerResult(t, commandGateway.sent());
        }
    }

    private Method handlerFor(PublishedEvent published) {
        var handler = reference.getEventHandler(published.getEventName());
        if (handler == null) {
            throw new AssertionError(reference.getComponentName() + " has no @EventHandler for "
                    + published.getEventName());
        }
        return handler;
    }

    private void handle(Event event, PublishedEvent published,
                        Method handler) throws Throwable {
        var message = published.getEventMessage();
        ReflectionUtils.invoke(reference.getRef(), handler,
                event, commandGateway, queryGateway, message, message.getMetadata(), status,
                Instant.ofEpochMilli(message.getTimestamp()), published.getEventSequenceNumber(), published);
    }
}
