package org.evento.application.manager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.ProjectionReference;
import org.evento.common.modeling.annotations.component.Projection;
import org.evento.common.modeling.exceptions.HandlerNotFoundException;
import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.messaging.message.application.QueryMessage;
import org.evento.common.modeling.messaging.query.SerializedQueryResponse;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The ProjectionManager class extends the ReceiverComponentManager class and is responsible for managing projections.
 * Projections are components capable of handling query messages.
 */
public class ProjectionManager extends ReceiverComponentManager<QueryMessage<?>, ProjectionReference> {
    private static final Logger logger = LogManager.getLogger(ProjectionManager.class);

    /**
     * Creates a new instance of ProjectionManager.
     *
     * @param bundleId                   The identifier of the bundle.
     * @param gatewayTelemetryProxy      The function used to create GatewayTelemetryProxy instances.
     * @param tracingAgent               The tracing agent used for tracing.
     */
    public ProjectionManager(String bundleId, BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy, TracingAgent tracingAgent) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent);
    }

    /**
     * Parses the classes annotated with @Projection and registers the query handlers.
     *
     * @param reflections            The reflections instance used for scanning classes.
     * @param findInjectableObject   The function used to find injectable objects.
     * @throws InvocationTargetException    If the class instantiation fails.
     * @throws InstantiationException       If the class instantiation fails.
     * @throws IllegalAccessException       If the class instantiation fails.
     */
    @Override
    public void parse(Reflections reflections, Function<Class<?>, Object> findInjectableObject)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Projection.class)) {
            var projectionReference = new ProjectionReference(createComponentInstance(aClass, findInjectableObject));
            for (String query : projectionReference.getRegisteredQueries()) {
                getHandlers().put(query, projectionReference);
                logger.info("Projection query handler for %s found in %s".formatted(query, projectionReference.getRef().getClass().getName()));
            }
        }
    }

    /**
     * Handles the given query message by invoking the appropriate query handler.
     *
     * @param q The query message to handle.
     * @return The serialized query response.
     * @throws Exception If an error occurs while handling the query.
     * @throws HandlerNotFoundException If no handler is found for the query in the bundle.
     */
    @Override
    public SerializedQueryResponse<?> handle(QueryMessage<?> q) throws Exception {
        var handler = getHandlers().get(q.getQueryName());
        if (handler == null)
            throw new HandlerNotFoundException("No handler found for %s in %s".formatted(q.getQueryName(), getBundleId()));
        var proxy = getGatewayTelemetryProxy().apply(handler.getComponentName(), q);
        return getTracingAgent().track(q, handler.getComponentName(),
                null,
                () -> {
                    var result = handler.invoke(
                            q,
                            proxy,
                            proxy
                    );
                    var rm = new SerializedQueryResponse<>(result);
                    proxy.sendInvocationsMetric();
                    return rm;
                });
    }
}
