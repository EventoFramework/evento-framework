package org.evento.application.reference;


import org.evento.application.utils.ReflectionUtils;
import org.evento.common.modeling.annotations.handler.SagaEventHandler;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.common.modeling.messaging.payload.Event;
import org.evento.common.modeling.state.SagaState;
import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.QueryGateway;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

public class SagaReference extends Reference{

	private HashMap<String, Method> sagaEventHandlerReferences = new HashMap<>();

	public SagaReference(Object ref) {
		super(ref);
		for (Method declaredMethod : ref.getClass().getDeclaredMethods())
		{

			var ach = declaredMethod.getAnnotation(SagaEventHandler.class);
			if(ach != null){
				sagaEventHandlerReferences.put(Arrays.stream(declaredMethod.getParameterTypes())
						.filter(Event.class::isAssignableFrom)
						.findFirst()
						.map(Class::getSimpleName).orElseThrow(), declaredMethod);
			}
		}
	}


	public Method getSagaEventHandler(String eventName){
		return sagaEventHandlerReferences.get(eventName);
	}

	public Set<String> getRegisteredEvents(){
		return sagaEventHandlerReferences.keySet();
	}

	public SagaState invoke(
			EventMessage<? extends Event> em,
			SagaState sagaState,
			CommandGateway commandGateway,
			QueryGateway queryGateway)
			throws Throwable {

		var handler = sagaEventHandlerReferences.get(em.getEventName());

		try
		{
			return (SagaState) ReflectionUtils.invoke(getRef(), handler,
					em.getPayload(),
					sagaState,
					commandGateway,
					queryGateway,
					em
			);
		}catch (InvocationTargetException e){
			throw e.getCause();
		}
	}
}