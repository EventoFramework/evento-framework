package org.eventrails.application.reference;

import org.eventrails.application.utils.ReflectionUtils;
import org.eventrails.common.modeling.annotations.handler.QueryHandler;
import org.eventrails.common.messaging.gateway.CommandGateway;
import org.eventrails.common.messaging.gateway.QueryGateway;
import org.eventrails.common.modeling.messaging.message.application.QueryMessage;
import org.eventrails.common.modeling.messaging.payload.Query;
import org.eventrails.common.modeling.messaging.query.QueryResponse;

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

	public Method getQueryHandler(String queryName){
		return queryHandlerReferences.get(queryName);
	}

	public QueryResponse<?> invoke(
			QueryMessage<?> qm,
			CommandGateway commandGateway,
			QueryGateway queryGateway)
			throws Throwable {

		var handler = queryHandlerReferences.get(qm.getQueryName());

		try
		{
			return (QueryResponse<?>) ReflectionUtils.invoke(getRef(), handler,
					qm.getPayload(),
					commandGateway,
					queryGateway,
					qm
			);
		}catch (InvocationTargetException e){
			throw e.getCause();
		}
	}
}
