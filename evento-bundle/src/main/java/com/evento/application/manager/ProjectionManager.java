package com.evento.application.manager;

import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.ProjectionReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.exceptions.HandlerNotFoundException;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.QueryMessage;
import com.evento.common.modeling.messaging.query.SerializedQueryResponse;
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
     * Constructs a new ProjectionManager instance.
     *
     * @param bundleId              The unique identifier of the bundle.
     * @param gatewayTelemetryProxy A function that, given a bundle ID and a message, provides an instance of the {@code GatewayTelemetryProxy}.
     * @param tracingAgent          The tracing agent used for telemetry and monitoring.
     * @param messageHandlerInterceptor    The interceptor for processing messages before or after execution.
     */
    public ProjectionManager(String bundleId, BiFunction<String, Message<?>,
                                     GatewayTelemetryProxy> gatewayTelemetryProxy,
                             TracingAgent tracingAgent,
                             MessageHandlerInterceptor messageHandlerInterceptor) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, messageHandlerInterceptor);
    }

    /**
     * Parses the classes annotated with @Projection and registers the query handlers.
     *
     * @param reflections          The reflections instance used for scanning classes.
     * @param findInjectableObject The function used to find injectable objects.
     * @throws InvocationTargetException If the class instantiation fails.
     * @throws InstantiationException    If the class instantiation fails.
     * @throws IllegalAccessException    If the class instantiation fails.
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
     * @throws Throwable                If an error occurs while handling the query.
     * @throws HandlerNotFoundException If no handler is found for the query in the bundle.
     */
    @Override
    public SerializedQueryResponse<?> handle(QueryMessage<?> q) throws Throwable {
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
                            proxy,
                            getMessageHandlerInterceptor()
                    );
                    var rm = new SerializedQueryResponse<>(result);
                    proxy.sendInvocationsMetric();
                    return rm;
                });
    }
}
