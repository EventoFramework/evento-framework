package com.evento.application.manager;

import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.SagaReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.messaging.message.application.Message;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SagaManager extends ConsumerComponentManager<SagaReference> {

    private static final Logger logger = LogManager.getLogger(SagaManager.class);

    public SagaManager(String bundleId, BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy,
                       TracingAgent tracingAgent,
                       int sssFetchSize, int sssFetchDelay, MessageHandlerInterceptor messageHandlerInterceptor) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, sssFetchSize, sssFetchDelay, messageHandlerInterceptor);
    }

    @Override
    public void parse(Reflections reflections, Function<Class<?>, Object> findInjectableObject) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Saga.class)) {
            var sagaReference = new SagaReference(createComponentInstance(aClass, findInjectableObject));
            getReferences().add(sagaReference);
            for (String event : sagaReference.getRegisteredEvents()) {
                var hl = getHandlers().getOrDefault(event, new HashMap<>());
                hl.put(aClass.getSimpleName(), sagaReference);
                getHandlers().put(event, hl);
                logger.info("Saga event handler for %s found in %s".formatted(event, sagaReference.getRef().getClass().getName()));
            }
        }
    }
}
