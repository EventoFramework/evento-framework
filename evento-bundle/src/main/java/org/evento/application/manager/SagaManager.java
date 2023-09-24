package org.evento.application.manager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.application.consumer.SagaEventConsumer;
import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.SagaReference;
import org.evento.common.messaging.consumer.ConsumerStateStore;
import org.evento.common.modeling.annotations.component.Saga;
import org.evento.common.modeling.messaging.message.application.Message;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class SagaManager extends ConsumerComponentManager<SagaReference> {

    private static final Logger logger = LogManager.getLogger(SagaManager.class);
    public SagaManager(String bundleId, BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy, TracingAgent tracingAgent, Supplier<Boolean> isShuttingDown, ConsumerStateStore consumerStateStore, int sssFetchSize, int sssFetchDelay) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, isShuttingDown, consumerStateStore, sssFetchSize, sssFetchDelay);
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

    public void startSagaEventConsumers() {
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
                        getConsumerStateStore(),
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
