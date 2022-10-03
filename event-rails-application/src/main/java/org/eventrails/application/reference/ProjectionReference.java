package org.eventrails.application.reference;

import org.eventrails.application.utils.ReflectionUtils;
import org.eventrails.modeling.annotations.handler.QueryHandler;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.gateway.QueryGateway;
import org.eventrails.modeling.messaging.message.QueryMessage;
import org.eventrails.modeling.messaging.payload.Query;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

public class ProjectionReference extends Reference{

	private HashMap<String, Method> queryHandlerReferences = new HashMap<>();

	public ProjectionReference(Object ref) {
		super(ref);
		for (Method declaredMethod : ref.getClass().getDeclaredMethods())
		{

			var ach = declaredMethod.getAnnotation(QueryHandler.class);
			if(ach != null){
				queryHandlerReferences.put(Arrays.stream(declaredMethod.getParameterTypes())
						.filter(Query.class::isAssignableFrom)
						.findFirst()
						.map(Class::getSimpleName).orElseThrow(), declaredMethod);
			}
		}
	}

	public Set<String> getRegisteredQueries(){
		return queryHandlerReferences.keySet();
	}

	public Object invoke(
			QueryMessage<? extends Query> qm,
			CommandGateway commandGateway,
			QueryGateway queryGateway)
			throws InvocationTargetException, IllegalAccessException {

		var handler = queryHandlerReferences.get(qm.getPayloadClass().getSimpleName());

		return ReflectionUtils.invoke(getRef(), handler,
				qm.getPayload(),
				commandGateway,
				queryGateway,
				qm
		);
	}
}
