package com.evento.application.manager;

import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.ObserverReference;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.modeling.annotations.component.Observer;
import com.evento.common.modeling.messaging.message.application.Message;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ObserverManager extends ConsumerComponentManager<ObserverReference> {
    private static final Logger logger = LoggerFactory.getLogger(ObserverManager.class);

    public ObserverManager(String bundleId, BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy,
                           TracingAgent tracingAgent,
                           int sssFetchSize, int sssFetchDelay, MessageHandlerInterceptor messageHandlerInterceptor) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, sssFetchSize, sssFetchDelay, messageHandlerInterceptor);
    }

    @Override
    public void parse(ComponentScan scan, Function<Class<?>, Object> findInjectableObject) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        for (Class<?> aClass : scan.annotatedWith(Observer.class)) {
            var observerReference = new ObserverReference(createComponentInstance(aClass, findInjectableObject));
            getReferences().add(observerReference);
            for (String event : observerReference.getRegisteredEvents()) {
                var hl = getHandlers().getOrDefault(event, new HashMap<>());
                hl.put(aClass.getSimpleName(), observerReference);
                getHandlers().put(event, hl);
                logger.info("Observer event handler for %s found in %s".formatted(event, observerReference.getRef().getClass().getName()));
            }
        }
    }
}
