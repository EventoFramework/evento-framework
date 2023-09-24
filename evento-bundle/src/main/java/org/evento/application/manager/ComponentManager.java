package org.evento.application.manager;

import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.Reference;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.modeling.messaging.message.application.Message;
import org.reflections.Reflections;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class ComponentManager{


    private final String bundleId;
    private final BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy;
    private final TracingAgent tracingAgent;

    protected ComponentManager(String bundleId, BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy, TracingAgent tracingAgent) {
        this.bundleId = bundleId;
        this.gatewayTelemetryProxy = gatewayTelemetryProxy;
        this.tracingAgent = tracingAgent;
    }


    protected Object createComponentInstance(Class<?> aClass, Function<Class<?>, Object> findInjectableObject) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        var constructor = aClass.getConstructors()[0];
        var parameters = new Object[constructor.getParameterTypes().length];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = findInjectableObject.apply(constructor.getParameterTypes()[i]);
        }
        return constructor.newInstance(parameters);
    }

    abstract public void parse(Reflections reflections, Function<Class<?>, Object> findInjectableObject) throws InvocationTargetException, InstantiationException, IllegalAccessException;


    public String getBundleId() {
        return bundleId;
    }

    public BiFunction<String, Message<?>, GatewayTelemetryProxy> getGatewayTelemetryProxy() {
        return gatewayTelemetryProxy;
    }

    public TracingAgent getTracingAgent() {
        return tracingAgent;
    }
}
