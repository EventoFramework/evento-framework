package org.evento.application.manager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.application.consumer.ProjectorEvenConsumer;
import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.ProjectorReference;
import org.evento.common.messaging.consumer.ConsumerStateStore;
import org.evento.common.modeling.annotations.component.Projector;
import org.evento.common.modeling.messaging.message.application.Message;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The ProjectorManager class is responsible for managing projectors and their event consumers.
 * It extends the ConsumerComponentManager class and provides methods to start event consumers
 * and parse projectors.
 */
public class ProjectorManager extends ConsumerComponentManager<ProjectorReference> {

    private static final Logger logger = LogManager.getLogger(ProjectorManager.class);

    /**
     * Constructs a ProjectorManager object with the specified parameters.
     *
     * @param bundleId                 the bundle ID associated with the ProjectorManager
     * @param gatewayTelemetryProxy    a function that takes a String and a Message object and returns a GatewayTelemetryProxy
     * @param tracingAgent             the TracingAgent object used for tracing
     * @param isShuttingDown           a supplier that provides a Boolean value indicating if the application is shutting down
     * @param sssFetchSize             the fetch size for SSS
     * @param sssFetchDelay            the fetch delay for SSS
     */
    public ProjectorManager(String bundleId, BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy, TracingAgent tracingAgent, Supplier<Boolean> isShuttingDown, int sssFetchSize, int sssFetchDelay) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, isShuttingDown, sssFetchSize, sssFetchDelay);
    }


    /**
     * Starts the event consumer for the ProjectorManager.
     *
     * @param onHeadReached     a Runnable that will be executed when the head is reached
     * @param consumerStateStore    the ConsumerStateStore to use for tracking consumer state
     * @throws Exception if an error occurs while starting the event consumer
     */
    public void startEventConsumer(Runnable onHeadReached, ConsumerStateStore consumerStateStore) throws Exception {
        if (getReferences().isEmpty()) {
            onHeadReached.run();
            return;
        }
        var counter = new AtomicInteger(getReferences().stream()
                .mapToInt(p -> p.getRef().getClass().getAnnotation(Projector.class).context().length).sum());
        logger.info("Checking for projector event consumers");
        for (ProjectorReference projector : getReferences()) {
            var annotation = projector.getRef().getClass().getAnnotation(Projector.class);
            for (var c : annotation.context()) {
                var projectorName = projector.getRef().getClass().getSimpleName();
                var projectorVersion = annotation.version();
                logger.info("Starting event consumer for Projector: %s - Version: %d - Context: %s"
                        .formatted(projectorName, projectorVersion, c));
                new Thread(new ProjectorEvenConsumer(
                        getBundleId(),
                        projectorName,
                        projectorVersion,
                        c,
                        getIsShuttingDown(),
                        consumerStateStore,
                        getHandlers(),
                        getTracingAgent(),
                        getGatewayTelemetryProxy(),
                        getSssFetchSize(),
                        getSssFetchDelay(),
                        counter,
                        onHeadReached
                )).start();
            }

        }

    }

    /**
     * Parses the classes annotated with @Projector and adds them as references to the ProjectorManager.
     * Also registers the projector event handlers.
     *
     * @param reflections           the Reflections object containing the annotated classes
     * @param findInjectableObject  a function that can find an injectable object by its class
     * @throws InvocationTargetException if an exception occurs while invoking a method or constructor via reflection
     * @throws InstantiationException    if a class cannot be instantiated
     * @throws IllegalAccessException    if access to a class, field, method, or constructor is denied
     */
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
