package com.evento.application.manager;

import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.ProjectorReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.messaging.message.application.Message;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ProjectorManager extends ConsumerComponentManager<ProjectorReference> {

    private static final Logger logger = LogManager.getLogger(ProjectorManager.class);

    public ProjectorManager(String bundleId, BiFunction<String, Message<?>,
                                    GatewayTelemetryProxy> gatewayTelemetryProxy,
                            TracingAgent tracingAgent,
                            int sssFetchSize, int sssFetchDelay,
                            MessageHandlerInterceptor messageHandlerInterceptor) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, sssFetchSize, sssFetchDelay, messageHandlerInterceptor);
    }

    public void parse(Reflections reflections, Function<Class<?>, Object> findInjectableObject) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Projector.class)) {
            var projectorReference = new ProjectorReference(createComponentInstance(aClass, findInjectableObject));
            getReferences().add(projectorReference);
            for (String event : projectorReference.getRegisteredEvents()) {
                var hl = getHandlers().getOrDefault(event, new HashMap<>());
                hl.put(aClass.getSimpleName(), projectorReference);
                getHandlers().put(event, hl);
                logger.info("Projector event handler for %s found in %s".formatted(event, projectorReference.getRef().getClass().getName()));
            }
        }
    }
}
