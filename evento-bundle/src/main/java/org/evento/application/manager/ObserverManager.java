package org.evento.application.manager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.ObserverReference;
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

/**
 * ObserverManager is a class that manages observers for handling events.
 * It extends the ConsumerComponentManager class and overrides its parse method.
 * It also provides a handle method to handle event messages.
 */
public class ObserverManager extends ConsumerComponentManager<ObserverReference> {
    private static final Logger logger = LogManager.getLogger(ObserverManager.class);
    /**
     * Creates a new ObserverManager.
     *
     * @param bundleId              the ID of the bundle
     * @param gatewayTelemetryProxy the function to create a GatewayTelemetryProxy
     * @param tracingAgent          the TracingAgent instance
     * @param isShuttingDown        the function to check if the application is shutting down
     * @param sssFetchSize          the fetch size for SSS requests
     * @param sssFetchDelay         the fetch delay for SSS requests
     */
    public ObserverManager(String bundleId, BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy, TracingAgent tracingAgent, Supplier<Boolean> isShuttingDown, int sssFetchSize, int sssFetchDelay) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, isShuttingDown, sssFetchSize, sssFetchDelay);
    }

    /**
     * Parses the given Reflections object to find classes annotated with @Observer,
     * creates ObserverReference instances for each class,
     * and registers the ObserverReference in the ObserverManager.
     *
     * @param reflections           the Reflections object to parse
     * @param findInjectableObject  the function to find an injectable object for a given class
     * @throws InvocationTargetException when an error occurs while invoking a method or constructor
     * @throws InstantiationException when a new instance of a class cannot be created
     * @throws IllegalAccessException when access to a class, field, method, or constructor is denied
     */
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

    /**
     * Handles an EventMessage by invoking the appropriate ObserverReference instances registered for the event.
     *
     * @param e the EventMessage to handle
     * @throws Exception when an error occurs during the handling process
     */
    public void handle(EventMessage<?> e) throws Exception {
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
