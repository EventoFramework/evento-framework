package com.evento.application.manager;

import com.evento.application.consumer.SagaEventConsumer;
import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.SagaReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.messaging.message.application.Message;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;



/**
 * The `SagaManager` class extends the `ConsumerComponentManager` class and serves as a manager for sagas.
 * It is responsible for parsing annotated classes, creating `SagaReference` objects, and starting saga event consumers.
 */
public class SagaManager extends ConsumerComponentManager<SagaReference> {

    private static final Logger logger = LogManager.getLogger(SagaManager.class);
    /**
     * Creates a new instance of SagaManager.
     *
     * @param bundleId                the bundle ID
     * @param gatewayTelemetryProxy   the gateway telemetry proxy
     * @param tracingAgent            the tracing agent
     * @param isShuttingDown          the supplier for checking if the application is shutting down
     * @param sssFetchSize            the size of the SSS fetch
     * @param sssFetchDelay           the delay for SSS fetch
     */
    public SagaManager(String bundleId, BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy, TracingAgent tracingAgent, Supplier<Boolean> isShuttingDown, int sssFetchSize, int sssFetchDelay) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, isShuttingDown, sssFetchSize, sssFetchDelay);
    }

    /**
     * Parses the annotated classes in the given Reflections instance and creates SagaReference
     * objects for each annotated class. Adds the SagaReference to the references list and
     * updates the handlers map with the registered events and corresponding SagaReferences.
     *
     * @param reflections           the Reflections instance to scan for annotated classes
     * @param findInjectableObject  the function to find injectable objects
     * @throws InvocationTargetException   if the called constructor throws an exception
     * @throws InstantiationException      if the class cannot be instantiated
     * @throws IllegalAccessException      if the constructor is inaccessible
     */
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

    /**
     * Starts the saga event consumers for the registered SagaReferences. Each SagaReference is checked for
     * the Saga annotation, and for each context specified in the annotation, a new SagaEventConsumer is created
     * and started in a new thread.
     *
     * @param consumerStateStore     the consumer state store to track the state of event consumers
     */
    public void startSagaEventConsumers(ConsumerStateStore consumerStateStore) {
        if (getReferences().isEmpty()) return;
        logger.info("Starting saga consumers");
        logger.info("Checking for saga event consumers");
        for (SagaReference saga : getReferences()) {
            var annotation = saga.getRef().getClass().getAnnotation(Saga.class);
            for (var c : annotation.context()) {
                var sagaName = saga.getRef().getClass().getSimpleName();
                var sagaVersion = annotation.version();
                logger.info("Starting event consumer for Saga: %s - Version: %d - Context: %s"
                        .formatted(sagaName, sagaVersion, c));
                new Thread(new SagaEventConsumer(
                        getBundleId(),
                        sagaName,
                        sagaVersion,
                        c,
                        getIsShuttingDown(),
                        consumerStateStore,
                        getHandlers(),
                        getTracingAgent(),
                        getGatewayTelemetryProxy(),
                        getSssFetchSize(),
                        getSssFetchDelay()
                )).start();
            }

        }
    }
}
