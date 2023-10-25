package org.evento.application.manager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.ServiceReference;
import org.evento.common.modeling.annotations.component.Service;
import org.evento.common.modeling.exceptions.HandlerNotFoundException;
import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.messaging.message.application.ServiceCommandMessage;
import org.evento.common.modeling.messaging.message.application.ServiceEventMessage;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ServiceManager extends ReceiverComponentManager<ServiceCommandMessage, ServiceReference> {
    private static final Logger logger = LogManager.getLogger(AggregateManager.class);

    public ServiceManager(String bundleId, BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy, TracingAgent tracingAgent) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent);
    }

    @Override
    public void parse(Reflections reflections, Function<Class<?>, Object> findInjectableObject)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Service.class)) {
            var serviceReference = new ServiceReference(createComponentInstance(aClass, findInjectableObject));
            for (String command : serviceReference.getRegisteredCommands()) {
                getHandlers().put(command, serviceReference);
                logger.info("Service command handler for %s found in %s".formatted(command, serviceReference.getRef().getClass().getName()));
            }
        }
    }

    @Override
    public ServiceEventMessage handle(ServiceCommandMessage c) throws Throwable {
        var handler = getHandlers().get(c.getCommandName());
        if (handler == null)
            throw new HandlerNotFoundException("No handler found for %s in %s"
                    .formatted(c.getCommandName(), getBundleId()));
        var proxy = getGatewayTelemetryProxy().apply(handler.getComponentName(), c);
        return getTracingAgent().track(c, handler.getComponentName(),
                null,
                () -> {
                    var event = handler.invoke(
                            c,
                            proxy,
                            proxy
                    );
                    var em = new ServiceEventMessage(event);
                    getTracingAgent().correlate(c, em);
                    proxy.sendInvocationsMetric();
                    return em;
                });
    }
}
