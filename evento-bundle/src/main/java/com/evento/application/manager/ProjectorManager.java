package com.evento.application.manager;

import com.evento.application.consumer.ProjectorEvenConsumer;
import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.ProjectorReference;
import com.evento.common.utils.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.messaging.message.application.Message;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
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

    private final ArrayList<ProjectorEvenConsumer> projectorEvenConsumers = new ArrayList<>();

    /**
     * Constructs an instance of the ProjectorManager.
     *
     * @param bundleId               The bundle identifier associated with the projector manager.
     * @param gatewayTelemetryProxy  A BiFunction that provides a gateway telemetry proxy for a given bundle ID
     *                                and message.
     * @param tracingAgent           The tracing agent used for monitoring and tracing events within the manager.
     * @param isShuttingDown         A supplier that indicates whether the application is in the process of shutting down.
     * @param sssFetchSize           The size of the fetch operation used in state storage synchronizations.
     * @param sssFetchDelay          The delay between fetch operations in state storage synchronizations in milliseconds.
     * @param messageHandlerInterceptor     The interceptor used for pre- and post-processing of messages handled by the manager.
     */
    public ProjectorManager(String bundleId, BiFunction<String, Message<?>,
                                    GatewayTelemetryProxy> gatewayTelemetryProxy,
                            TracingAgent tracingAgent, Supplier<Boolean> isShuttingDown,
                            int sssFetchSize, int sssFetchDelay,
                            MessageHandlerInterceptor messageHandlerInterceptor) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, isShuttingDown, sssFetchSize, sssFetchDelay, messageHandlerInterceptor);
    }


    /**
     * Retrieves the collection of ProjectorEvenConsumers.
     *
     * @return the collection of ProjectorEvenConsumers stored in the ProjectorManager.
     */
    public Collection<ProjectorEvenConsumer> getProjectorEvenConsumers() {
        return projectorEvenConsumers;
    }




    /**
     * Starts the event consumers for projectors.
     *
     * @param onAllHeadReached    the runnable that gets executed when all heads have been reached
     * @param consumerStateStore  the consumer state store used by the event consumers
     * @param contexts            the map containing the consumers contexts association
     */
    public void startEventConsumers(Runnable onAllHeadReached, ConsumerStateStore consumerStateStore, Map<String,Set<String>> contexts) {
        if (getReferences().isEmpty()) {
            onAllHeadReached.run();
            return;
        }
        var counter = new AtomicInteger(getReferences()
                .stream()
                .mapToInt(p -> contexts.getOrDefault(p.getComponentName(), Set.of(Context.ALL))
                        .size())
                .sum());
        logger.info("Checking for projector event consumers");
        for (ProjectorReference projector : getReferences()) {
            var annotation = projector.getRef().getClass().getAnnotation(Projector.class);
            for (var context : contexts.getOrDefault(projector.getComponentName(), Set.of(Context.ALL))) {
                var projectorName = projector.getRef().getClass().getSimpleName();
                var projectorVersion = annotation.version();
                logger.info("Starting event consumer for Projector: {} - Version: {} - Context: %{}"
                        ,projectorName, projectorVersion, context);
                var c = new ProjectorEvenConsumer(
                        getBundleId(),
                        projectorName,
                        projectorVersion,
                        context,
                        getIsShuttingDown(),
                        consumerStateStore,
                        getHandlers(),
                        getTracingAgent(),
                        getGatewayTelemetryProxy(),
                        getSssFetchSize(),
                        getSssFetchDelay(),
                        counter,
                        onAllHeadReached,
                        getMessageHandlerInterceptor()
                );
                projectorEvenConsumers.add(c);
                var t = new Thread(c);
                t.setName(projectorName + "(v"+projectorVersion+") - " + context);
                t.start();
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
