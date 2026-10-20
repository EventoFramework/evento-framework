package com.evento.test;

import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.application.reference.ServiceReference;
import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.messaging.message.application.ServiceCommandMessage;
import com.evento.common.modeling.messaging.payload.ServiceCommand;

/**
 * Given/when/then test of a {@link Service}: the {@code @CommandHandler} runs as in a bundle, on
 * the service instance you build with its collaborators (a payment client, a repository).
 *
 * <pre>{@code
 * ServiceFixture.of(new PaymentService(fakeGateway))
 *     .when(new ChargeCommand("o1", 30))
 *     .expectEvent(new OrderChargedEvent("o1", 30));
 * }</pre>
 *
 * @param <S> the service class
 */
public final class ServiceFixture<S> {

    private final ServiceReference reference;
    private final RecordingCommandGateway commandGateway = new RecordingCommandGateway();
    private final StubQueryGateway queryGateway = new StubQueryGateway();

    private ServiceFixture(S service) {
        if (service.getClass().getAnnotation(Service.class) == null) {
            throw new IllegalArgumentException(service.getClass().getName() + " is not annotated with @Service");
        }
        this.reference = new ServiceReference(service);
    }

    /** A fixture around an instance of the service. */
    public static <S> ServiceFixture<S> of(S service) {
        // @PersonalData payloads are written and read with keys in memory, unless the test installed others.
        com.evento.common.personaldata.SubjectKeys.installIfAbsent(
                new com.evento.common.personaldata.InMemorySubjectKeyProvider());
        return new ServiceFixture<>(service);
    }

    /** Commands the handler sends, recorded; stub their results with {@code answer}. */
    public RecordingCommandGateway commandGateway() {
        return commandGateway;
    }

    /** Queries the handler asks, answered by the stubs registered with {@code answer}. */
    public StubQueryGateway queryGateway() {
        return queryGateway;
    }

    /** Handle {@code command} and return the event, or the exception. */
    public ServiceResult when(ServiceCommand command) {
        var message = new ServiceCommandMessage(command);
        if (reference.getAggregateCommandHandler(message.getCommandName()) == null) {
            throw new AssertionError(reference.getComponentName() + " has no @CommandHandler for "
                    + message.getCommandName());
        }
        commandGateway.clear();
        try {
            var event = reference.invoke(message, commandGateway, queryGateway, new MessageHandlerInterceptor() {
            });
            return new ServiceResult(event, null, commandGateway.sent());
        } catch (Throwable t) {
            return new ServiceResult(null, t, commandGateway.sent());
        }
    }
}
