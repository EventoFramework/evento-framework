package com.evento.application.manager;

import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.ServiceReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.exceptions.HandlerNotFoundException;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.messaging.message.application.ServiceCommandMessage;
import com.evento.common.modeling.messaging.message.application.ServiceEventMessage;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The ServiceManager class is responsible for managing service commands
 * and their associated handlers. It extends the ReceiverComponentManager class
 * and implements the required methods for parsing and handling the service commands.
 */
public class ServiceManager extends ReceiverComponentManager<ServiceCommandMessage, ServiceReference> {
    private static final Logger logger = LogManager.getLogger(ServiceManager.class);

    /**
     * Creates a new instance of ServiceManager with the specified parameters.
     *
     * @param bundleId                   the ID of the bundle associated with the ServiceManager
     * @param gatewayTelemetryProxy      a function that maps a service name and a Message object to a GatewayTelemetryProxy
     * @param tracingAgent               the tracing agent to be used by the ServiceManager
     */
    public ServiceManager(String bundleId, BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy, TracingAgent tracingAgent) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent);
    }

    /**
     * Parses the classes annotated with @Service in the given Reflections object and registers their command handlers.
     *
     * @param reflections             the Reflections object containing the scanned classes
     * @param findInjectableObject    a function that finds an instance of the specified class to be injected into the service objects
     * @throws InvocationTargetException if an error occurs while invoking a method or constructor
     * @throws InstantiationException    if an error occurs while creating an instance of a service object
     * @throws IllegalAccessException    if access to a class, method, or field is denied
     */
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

    /**
     * Handles a service command by invoking the appropriate handler for the command name.
     *
     * @param c the ServiceCommandMessage object containing the command name and payload
     * @return a ServiceEventMessage object representing the result of the command execution
     * @throws HandlerNotFoundException   if no handler is found for the specified command name
     * @throws Exception                  if an error occurs during command handling
     */
    @Override
    public ServiceEventMessage handle(ServiceCommandMessage c) throws Exception {
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
