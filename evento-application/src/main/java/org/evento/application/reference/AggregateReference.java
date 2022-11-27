package org.evento.application.reference;


import org.evento.application.utils.ReflectionUtils;
import org.evento.common.modeling.exceptions.AggregateDeletedError;
import org.evento.common.modeling.exceptions.AggregateInitializedError;
import org.evento.common.modeling.exceptions.AggregateNotInitializedError;
import org.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import org.evento.common.modeling.annotations.handler.EventSourcingHandler;
import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.QueryGateway;
import org.evento.common.modeling.messaging.message.application.DomainCommandMessage;
import org.evento.common.modeling.messaging.message.application.DomainEventMessage;
import org.evento.common.modeling.messaging.payload.DomainCommand;
import org.evento.common.modeling.messaging.payload.DomainEvent;
import org.evento.common.modeling.state.AggregateState;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

public class AggregateReference extends Reference {

	private final HashMap<String, Method> eventSourcingReferences = new HashMap<>();

	private final HashMap<String, Method> aggregateCommandHandlerReferences = new HashMap<>();
	private final int snapshotFrequency;

	public AggregateReference(Object ref, int snapshotFrequency) {
		super(ref);
		this.snapshotFrequency = snapshotFrequency;
		for (Method declaredMethod : ref.getClass().getDeclaredMethods())
		{
			var esh = declaredMethod.getAnnotation(EventSourcingHandler.class);
			if (esh != null)
			{
				eventSourcingReferences.put(
						Arrays.stream(declaredMethod.getParameterTypes())
								.filter(DomainEvent.class::isAssignableFrom)
								.findFirst()
								.map(Class::getSimpleName).orElseThrow(),
						declaredMethod);
				continue;
			}

			var ach = declaredMethod.getAnnotation(AggregateCommandHandler.class);
			if (ach != null)
			{
				aggregateCommandHandlerReferences.put(Arrays.stream(declaredMethod.getParameterTypes())
						.filter(DomainCommand.class::isAssignableFrom)
						.findFirst()
						.map(Class::getSimpleName).orElseThrow(), declaredMethod);
			}
		}
	}

	public int getSnapshotFrequency() {
		return snapshotFrequency;
	}

	public Method getEventSourcingHandler(String eventName) {
		return eventSourcingReferences.get(eventName);
	}

	public Method getAggregateCommandHandler(String eventName) {
		return aggregateCommandHandlerReferences.get(eventName);
	}

	public Set<String> getRegisteredCommands() {
		return aggregateCommandHandlerReferences.keySet();
	}

	public DomainEvent invoke(
			DomainCommandMessage cm,
			AggregateStateEnvelope envelope,
			Collection<DomainEventMessage> eventStream,
			CommandGateway commandGateway,
			QueryGateway queryGateway)
			throws Throwable {

		var commandHandler = aggregateCommandHandlerReferences.get(cm.getCommandName());

		if (eventStream.isEmpty() && envelope.getAggregateState() == null && !isAggregateInitializer(commandHandler))
			throw AggregateNotInitializedError.build(cm.getAggregateId());
		if ((!eventStream.isEmpty() || envelope.getAggregateState() != null ) && isAggregateInitializer(commandHandler))
			throw AggregateInitializedError.build(cm.getAggregateId());

		try
		{
			for (var em : eventStream)
			{
				var eh = getEventSourcingHandler(em.getEventName());
				envelope.setAggregateState((AggregateState) ReflectionUtils.invoke(getRef(), eh, em.getPayload(), envelope.getAggregateState()));
				if (envelope.getAggregateState().isDeleted())
					throw AggregateDeletedError.build(cm.getAggregateId());
			}

			return (DomainEvent) ReflectionUtils.invoke(getRef(), commandHandler,
					cm.getPayload(),
					envelope.getAggregateState(),
					commandGateway,
					queryGateway,
					cm
			);
		}catch (InvocationTargetException e){
			throw e.getCause();
		}
	}

	private boolean isAggregateInitializer(Method commandHandler) {
		return commandHandler.getAnnotation(AggregateCommandHandler.class).init();
	}
}
