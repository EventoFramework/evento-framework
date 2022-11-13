package org.eventrails.application.reference;

import org.eventrails.application.utils.ReflectionUtils;
import org.eventrails.common.modeling.annotations.handler.EventHandler;
import org.eventrails.common.messaging.gateway.CommandGateway;
import org.eventrails.common.messaging.gateway.QueryGateway;
import org.eventrails.common.modeling.messaging.message.application.EventMessage;
import org.eventrails.common.modeling.messaging.payload.Event;
import org.eventrails.common.modeling.bundle.TransactionalProjector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

public class ProjectorReference extends Reference implements TransactionalProjector {

	private HashMap<String, Method> eventHandlerReferences = new HashMap<>();

	public ProjectorReference(Object ref) {
		super(ref);
		for (Method declaredMethod : ref.getClass().getDeclaredMethods())
		{

			var ach = declaredMethod.getAnnotation(EventHandler.class);
			if (ach != null)
			{
				eventHandlerReferences.put(Arrays.stream(declaredMethod.getParameterTypes())
						.filter(Event.class::isAssignableFrom)
						.findFirst()
						.map(Class::getSimpleName).orElseThrow(), declaredMethod);
			}
		}
	}

	public Set<String> getRegisteredEvents() {
		return eventHandlerReferences.keySet();
	}

	public void invoke(
			EventMessage<? extends Event> em,
			CommandGateway commandGateway,
			QueryGateway queryGateway)
			throws Throwable {

		var handler = eventHandlerReferences.get(em.getEventName());

		try
		{
			ReflectionUtils.invoke(getRef(), handler,
					em.getPayload(),
					commandGateway,
					queryGateway,
					em
			);
		}catch (InvocationTargetException e){
			throw e.getCause();
		}
	}

	@Override
	public void begin() throws Exception {

	}

	@Override
	public void commit() throws Exception {

	}

	@Override
	public void rollback() throws Exception {

	}
}
