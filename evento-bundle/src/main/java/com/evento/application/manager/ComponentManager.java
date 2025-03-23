package com.evento.application.manager;

import com.evento.application.performance.TracingAgent;
import lombok.Getter;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.modeling.messaging.message.application.Message;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The `ComponentManager` class serves as a base class for managing components with common functionalities.
 */
@Getter
public abstract class ComponentManager {


    private final String bundleId;
    private final BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy;
    private final TracingAgent tracingAgent;
    private final MessageHandlerInterceptor messageHandlerInterceptor;

    /**
     * Constructs a `ComponentManager`.
     *
     * @param bundleId              The bundle identifier.
     * @param gatewayTelemetryProxy A function to create a `GatewayTelemetryProxy`.
     * @param tracingAgent          The tracing agent for telemetry.
     * @param messageHandlerInterceptor    Message Interceptor
     */
    protected ComponentManager(String bundleId, BiFunction<String, Message<?>,
                                       GatewayTelemetryProxy> gatewayTelemetryProxy,
                               TracingAgent tracingAgent, MessageHandlerInterceptor messageHandlerInterceptor) {
        this.bundleId = bundleId;
        this.gatewayTelemetryProxy = gatewayTelemetryProxy;
        this.tracingAgent = tracingAgent;
        this.messageHandlerInterceptor = messageHandlerInterceptor;
    }

    /**
     * Creates an instance of a component class by resolving its dependencies.
     *
     * @param aClass             The class to be instantiated.
     * @param findInjectableObject A function to find injectable objects.
     * @return An instance of the component class with resolved dependencies.
     * @throws InvocationTargetException If there is an issue with invoking a method.
     * @throws InstantiationException    If there is an issue with instantiating a class.
     * @throws IllegalAccessException    If there is an issue with accessing a class or its members.
     */
    protected Object createComponentInstance(Class<?> aClass, Function<Class<?>, Object> findInjectableObject) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        var constructor = aClass.getConstructors()[0];
        var parameters = new Object[constructor.getParameterTypes().length];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = findInjectableObject.apply(constructor.getParameterTypes()[i]);
        }
        return constructor.newInstance(parameters);
    }

    /**
     * Abstract method to parse and process components.
     *
     * @param reflections           The `Reflections` instance to discover and process components.
     * @param findInjectableObject   A function to find injectable objects.
     * @throws InvocationTargetException If there is an issue with invoking a method.
     * @throws InstantiationException    If there is an issue with instantiating a class.
     * @throws IllegalAccessException    If there is an issue with accessing a class or its members.
     */
    abstract public void parse(Reflections reflections, Function<Class<?>, Object> findInjectableObject) throws InvocationTargetException, InstantiationException, IllegalAccessException;

}
