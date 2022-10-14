package org.eventrails.application.reference;


import org.eventrails.application.utils.ReflectionUtils;
import org.eventrails.modeling.annotations.handler.CommandHandler;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.gateway.QueryGateway;
import org.eventrails.modeling.messaging.message.CommandMessage;
import org.eventrails.modeling.messaging.payload.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

public class ServiceReference extends Reference {


	private HashMap<String, Method> serviceCommandHandlerReferences = new HashMap<>();

	public ServiceReference(Object ref) {
		super(ref);
		for (Method declaredMethod : ref.getClass().getDeclaredMethods())
		{

			var ach = declaredMethod.getAnnotation(CommandHandler.class);
			if(ach != null){
				serviceCommandHandlerReferences.put(Arrays.stream(declaredMethod.getParameterTypes())
						.filter(ServiceCommand.class::isAssignableFrom)
						.findFirst()
						.map(Class::getSimpleName).orElseThrow(), declaredMethod);
			}
		}
	}


	public Method getAggregateCommandHandler(String eventName){
		return serviceCommandHandlerReferences.get(eventName);
	}

	public Set<String> getRegisteredCommands(){
		return serviceCommandHandlerReferences.keySet();
	}

	public ServiceEvent invoke(
			CommandMessage<? extends ServiceCommand> cm,
			CommandGateway commandGateway,
			QueryGateway queryGateway)
			throws Throwable {

		var commandHandler = serviceCommandHandlerReferences.get(cm.getPayloadClass().getSimpleName());

		try
		{
			return (ServiceEvent) ReflectionUtils.invoke(getRef(), commandHandler,
					cm.getPayload(),
					commandGateway,
					queryGateway,
					cm
			);
		}catch (InvocationTargetException e){
			throw  e.getCause();
		}
	}
}
