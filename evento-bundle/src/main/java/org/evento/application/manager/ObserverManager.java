package org.evento.application.manager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.ObserverReference;
import org.evento.common.messaging.consumer.ConsumerStateStore;
import org.evento.common.modeling.annotations.component.Observer;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.common.modeling.messaging.message.application.Message;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class ObserverManager extends ConsumerComponentManager<ObserverReference> {
    private static final Logger logger = LogManager.getLogger(ObserverManager.class);
    public ObserverManager(String bundleId, BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy, TracingAgent tracingAgent, Supplier<Boolean> isShuttingDown, ConsumerStateStore consumerStateStore, int sssFetchSize, int sssFetchDelay) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, isShuttingDown, consumerStateStore, sssFetchSize, sssFetchDelay);
    }

    @Override
    public void parse(Reflections reflections, Function<Class<?>, Object> findInjectableObject) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Observer.class)) {
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

    public void handle(EventMessage<?> e) throws Throwable {
        for (ObserverReference observerReference : getHandlers().get(e.getEventName()).values()) {
            var start = Instant.now();
            var proxy = getGatewayTelemetryProxy().apply(observerReference.getComponentName(), e);
            getTracingAgent().track(e, observerReference.getComponentName(),
                    null,
                    () -> {
                        observerReference.invoke(e, proxy, proxy);
                        proxy.sendServiceTimeMetric(start);
                        return null;
                    });
        }
    }
}
