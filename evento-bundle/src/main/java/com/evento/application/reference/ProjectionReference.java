package com.evento.application.reference;

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
     * Invokes a query handler method based on the given query message.
     *
     * @param qm              The query message containing the query and its payload.
     * @param commandGateway  The command gateway used for sending commands.
     * @param queryGateway    The query gateway used for sending queries.
     * @return The result of invoking the query handler method.
     * @throws Exception if an error occurs while invoking the query handler method.
     */
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
                qm.getMetadata(),
                Instant.ofEpochMilli(qm.getTimestamp())
        );
    }
}
