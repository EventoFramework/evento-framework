package org.eventrails.application.reference;


import org.eventrails.application.utils.ReflectionUtils;
import org.eventrails.modeling.annotations.handler.AggregateCommandHandler;
import org.eventrails.modeling.annotations.handler.EventSourcingHandler;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.gateway.QueryGateway;
import org.eventrails.modeling.messaging.message.CommandMessage;
import org.eventrails.modeling.messaging.message.DomainCommandMessage;
import org.eventrails.modeling.messaging.message.DomainEventMessage;
import org.eventrails.modeling.messaging.message.EventMessage;
import org.eventrails.modeling.messaging.payload.DomainCommand;
import org.eventrails.modeling.messaging.payload.DomainEvent;
import org.eventrails.modeling.messaging.payload.Event;
import org.eventrails.modeling.state.AggregateState;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

public class AggregateReference extends Reference{

	private HashMap<String, Method> eventSourcingReferences = new HashMap<>();

	private HashMap<String, Method> aggregateCommandHandlerReferences = new HashMap<>();

	public AggregateReference(Object ref) {
		super(ref);
		for (Method declaredMethod : ref.getClass().getDeclaredMethods())
		{
			var esh = declaredMethod.getAnnotation(EventSourcingHandler.class);
			if(esh != null){
				eventSourcingReferences.put(
						Arrays.stream(declaredMethod.getParameterTypes())
								.filter(DomainEvent.class::isAssignableFrom)
								.findFirst()
								.map(Class::getSimpleName).orElseThrow(),
						declaredMethod);
				continue;
			}

			var ach = declaredMethod.getAnnotation(AggregateCommandHandler.class);
			if(ach != null){
				aggregateCommandHandlerReferences.put(Arrays.stream(declaredMethod.getParameterTypes())
						.filter(DomainCommand.class::isAssignableFrom)
						.findFirst()
						.map(Class::getSimpleName).orElseThrow(), declaredMethod);
			}
		}
	}

	public Method getEventSourcingHandler(String eventName){
		return eventSourcingReferences.get(eventName);
	}

	public Method getAggregateCommandHandler(String eventName){
		return aggregateCommandHandlerReferences.get(eventName);
	}

	public Set<String> getRegisteredCommands(){
		return aggregateCommandHandlerReferences.keySet();
	}

	public Event invoke(
			DomainCommandMessage cm,
			AggregateState aggregateState,
			Collection<DomainEventMessage> eventStream,
			CommandGateway commandGateway,
			QueryGateway queryGateway)
			throws InvocationTargetException, IllegalAccessException {

		var commandHandler = aggregateCommandHandlerReferences.get(cm.getPayloadClass().getSimpleName());

		AggregateState currentAggregateState = aggregateState;
		for (var em : eventStream)
		{
			var eh = getEventSourcingHandler(em.getPayloadClass().getSimpleName());
			currentAggregateState = (AggregateState) ReflectionUtils.invoke(getRef(), eh, em.getPayload(), currentAggregateState);
		}

		return (Event) ReflectionUtils.invoke(getRef(), commandHandler,
						cm.getPayload(),
						currentAggregateState,
						commandGateway,
						queryGateway,
						cm
						);
	}
}
