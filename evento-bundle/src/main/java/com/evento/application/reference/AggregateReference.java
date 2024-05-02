package com.evento.application.reference;

import lombok.Getter;
import com.evento.application.utils.ReflectionUtils;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.common.modeling.exceptions.AggregateDeletedError;
import com.evento.common.modeling.exceptions.AggregateInitializedError;
import com.evento.common.modeling.exceptions.AggregateNotInitializedError;
import com.evento.common.modeling.messaging.message.application.DomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.payload.DomainCommand;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.state.AggregateState;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

/**
 * The `AggregateReference` class is responsible for managing aggregate references and handling domain commands and events.
 */
public class AggregateReference extends Reference {

    private final HashMap<String, Method> eventSourcingReferences = new HashMap<>();
    private final HashMap<String, Method> aggregateCommandHandlerReferences = new HashMap<>();

    @Getter
    private final int snapshotFrequency;

    /**
     * Constructs an `AggregateReference`.
     *
     * @param ref              The reference to the aggregate.
     * @param snapshotFrequency The snapshot frequency for the aggregate.
     */
    public AggregateReference(Object ref, int snapshotFrequency) {
        super(ref);
        this.snapshotFrequency = snapshotFrequency;
        for (Method declaredMethod : ref.getClass().getDeclaredMethods()) {
            var esh = declaredMethod.getAnnotation(EventSourcingHandler.class);
            if (esh != null) {
                eventSourcingReferences.put(
                        Arrays.stream(declaredMethod.getParameterTypes())
                                .filter(DomainEvent.class::isAssignableFrom)
                                .findFirst()
                                .map(Class::getSimpleName)
                                .orElseThrow(() -> new IllegalArgumentException("DomainEvent parameter not fount in  " + declaredMethod)),
                        declaredMethod);
                continue;
            }

            var ach = declaredMethod.getAnnotation(AggregateCommandHandler.class);
            if (ach != null) {
                aggregateCommandHandlerReferences.put(Arrays.stream(declaredMethod.getParameterTypes())
                        .filter(DomainCommand.class::isAssignableFrom)
                        .findFirst()
                        .map(Class::getSimpleName)
                        .orElseThrow(() -> new IllegalArgumentException("DomainCommand parameter not fount in  " + declaredMethod)), declaredMethod);
            }
        }
    }

    /**
     * Get the event sourcing handler method for a given event name.
     *
     * @param eventName The name of the event.
     * @return The event sourcing handler method.
     */
    public Method getEventSourcingHandler(String eventName) {
        return eventSourcingReferences.get(eventName);
    }

    /**
     * Get the aggregate command handler method for a given command name.
     *
     * @param commandName The name of the command.
     * @return The aggregate command handler method.
     */
    public Method getAggregateCommandHandler(String commandName) {
        return aggregateCommandHandlerReferences.get(commandName);
    }

    /**
     * Get the set of registered commands associated with this aggregate reference.
     *
     * @return A set of registered command names.
     */
    public Set<String> getRegisteredCommands() {
        return aggregateCommandHandlerReferences.keySet();
    }

    /**
     * Invoke a domain command on the aggregate and update its state.
     *
     * @param cm             The domain command message.
     * @param envelope       The aggregate state envelope.
     * @param eventStream    The collection of domain event messages.
     * @param commandGateway The command gateway for issuing commands.
     * @param queryGateway   The query gateway for querying data.
     * @return The domain event resulting from the command execution.
     * @throws Exception If there is an error during command handling.
     */
    public DomainEvent invoke(
            DomainCommandMessage cm,
            AggregateStateEnvelope envelope,
            Collection<DomainEventMessage> eventStream,
            CommandGateway commandGateway,
            QueryGateway queryGateway)
            throws Exception {

        var commandHandler = aggregateCommandHandlerReferences.get(cm.getCommandName());

        if (eventStream.isEmpty() && envelope.getAggregateState() == null && !isAggregateInitializer(commandHandler))
            throw AggregateNotInitializedError.build(cm.getAggregateId());
        if ((!eventStream.isEmpty() || envelope.getAggregateState() != null) && isAggregateInitializer(commandHandler))
            throw AggregateInitializedError.build(cm.getAggregateId());

        for (var em : eventStream) {
            var eh = getEventSourcingHandler(em.getEventName());
            if(eh != null){
                var state = (AggregateState) ReflectionUtils.invoke(getRef(), eh, em.getPayload(), envelope.getAggregateState(), em.getMetadata());
                if (state == null) {
                    state = envelope.getAggregateState();
                }
                envelope.setAggregateState(state);
                if (envelope.getAggregateState().isDeleted())
                    throw AggregateDeletedError.build(cm.getAggregateId());
            }
        }

        var resp =  (DomainEvent) ReflectionUtils.invoke(getRef(), commandHandler,
                cm.getPayload(),
                envelope.getAggregateState(),
                commandGateway,
                queryGateway,
                cm,
                cm.getMetadata()
        );
        var eh = getEventSourcingHandler(resp.getClass().getSimpleName());
        var state = (AggregateState) ReflectionUtils.invoke(getRef(), eh, resp, envelope.getAggregateState(), cm.getMetadata());
        if (state == null) {
            state = envelope.getAggregateState();
        }
        envelope.setAggregateState(state);
        return resp;
    }

    /**
     * Check if the provided command handler is an aggregate initializer.
     *
     * @param commandHandler The aggregate command handler method.
     * @return `true` if it is an initializer, otherwise `false`.
     */
    private boolean isAggregateInitializer(Method commandHandler) {
        return commandHandler.getAnnotation(AggregateCommandHandler.class).init();
    }
}
