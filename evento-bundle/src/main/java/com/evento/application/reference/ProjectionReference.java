package com.evento.application.reference;

import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.application.utils.ReflectionUtils;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.message.application.QueryMessage;
import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.QueryResponse;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

/**
 * The ProjectionReference class extends the Reference class and is responsible for managing query handler references
 * for projection objects.
 */
public class ProjectionReference extends Reference {

    private final HashMap<String, Method> queryHandlerReferences = new HashMap<>();

    /**
     * The ProjectionReference class extends the Reference class and is responsible for managing query handler references
     * for projection objects.
     * @param ref the Projection Object
     */
    public ProjectionReference(Object ref) {
        super(ref);
        for (Method declaredMethod : ref.getClass().getDeclaredMethods()) {

            var ach = declaredMethod.getAnnotation(QueryHandler.class);
            if (ach != null) {
                queryHandlerReferences.put(Arrays.stream(declaredMethod.getParameterTypes())
                        .filter(Query.class::isAssignableFrom)
                        .findFirst()
                        .map(Class::getSimpleName)
                        .orElseThrow(() -> new IllegalArgumentException("Query parameter not fount in  " + declaredMethod)), declaredMethod);
            }
        }
    }

    /**
     * Retrieves the set of registered queries for query handlers managed by the ProjectionReference class.
     *
     * @return The set of registered queries as a Set of Strings.
     */
    public Set<String> getRegisteredQueries() {
        return queryHandlerReferences.keySet();
    }

    /**
     * Retrieves the query handler method for the specified query name.
     *
     * @param queryName The name of the query.
     * @return The query handler method as a Method object, or null if no query handler is found for the given name.
     */
    public Method getQueryHandler(String queryName) {
        return queryHandlerReferences.get(queryName);
    }

    /**
     * Invokes the appropriate query handler method based on the specified QueryMessage.
     *
     * @param qm                      The QueryMessage containing the query to be handled.
     * @param commandGateway          The gateway used for sending commands as part of the query processing.
     * @param queryGateway            The gateway used for sending and handling queries.
     * @param messageHandlerInterceptor The interceptor for handling operations before and after query execution, and for managing exceptions.
     * @return The result of the query execution wrapped in a QueryResponse object.
     * @throws Throwable              If an exception occurs during the invocation or query execution.
     */
    public QueryResponse<?> invoke(
            QueryMessage<?> qm,
            CommandGateway commandGateway,
            QueryGateway queryGateway, MessageHandlerInterceptor messageHandlerInterceptor)
            throws Throwable {

        var handler = queryHandlerReferences.get(qm.getQueryName());

        try {
            messageHandlerInterceptor.beforeProjectionQueryHandling(
                    getRef().getClass(),
                    qm,
                    queryGateway
            );

            var resp = (QueryResponse<?>) ReflectionUtils.invoke(getRef(), handler,
                    qm.getPayload(),
                    commandGateway,
                    queryGateway,
                    qm,
                    qm.getMetadata(),
                    Instant.ofEpochMilli(qm.getTimestamp())
            );

            return messageHandlerInterceptor.afterProjectionQueryHandling(
                    getRef().getClass(),
                    qm,
                    queryGateway,
                    resp
            );
        }catch (Throwable t){
            throw messageHandlerInterceptor.onExceptionProjectionQueryHandling(
                    getRef().getClass(),
                    qm,
                    queryGateway,
                    t
            );
        }
    }
}
