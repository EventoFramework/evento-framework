package org.evento.application.reference;

import org.evento.application.utils.ReflectionUtils;
import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.QueryGateway;
import org.evento.common.modeling.annotations.handler.QueryHandler;
import org.evento.common.modeling.messaging.message.application.QueryMessage;
import org.evento.common.modeling.messaging.payload.Query;
import org.evento.common.modeling.messaging.query.QueryResponse;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

public class ProjectionReference extends Reference {

    private HashMap<String, Method> queryHandlerReferences = new HashMap<>();

    public ProjectionReference(Object ref) {
        super(ref);
        for (Method declaredMethod : ref.getClass().getDeclaredMethods()) {

            var ach = declaredMethod.getAnnotation(QueryHandler.class);
            if (ach != null) {
                queryHandlerReferences.put(Arrays.stream(declaredMethod.getParameterTypes())
                        .filter(Query.class::isAssignableFrom)
                        .findFirst()
                        .map(Class::getSimpleName).orElseThrow(), declaredMethod);
            }
        }
    }

    public Set<String> getRegisteredQueries() {
        return queryHandlerReferences.keySet();
    }

    public Method getQueryHandler(String queryName) {
        return queryHandlerReferences.get(queryName);
    }

    public QueryResponse<?> invoke(
            QueryMessage<?> qm,
            CommandGateway commandGateway,
            QueryGateway queryGateway)
            throws Exception {

        var handler = queryHandlerReferences.get(qm.getQueryName());

        return (QueryResponse<?>) ReflectionUtils.invoke(getRef(), handler,
                qm.getPayload(),
                commandGateway,
                queryGateway,
                qm,
                qm.getMetadata()
        );
    }
}
