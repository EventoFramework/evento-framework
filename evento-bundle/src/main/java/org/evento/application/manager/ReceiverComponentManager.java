package org.evento.application.manager;

import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.ProjectorReference;
import org.evento.application.reference.Reference;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.modeling.messaging.message.application.Message;
import org.reflections.Reflections;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class ReceiverComponentManager<M extends Serializable, R extends Reference> extends ComponentManager {

    private final HashMap<String, R> handlers = new HashMap<>();
    protected ReceiverComponentManager(
            String bundleId,
            BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy,
            TracingAgent tracingAgent) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent);
    }

    abstract public void handle(
            M c,
            MessageBus.MessageBusResponseSender response) throws Throwable;


    public HashMap<String, R> getHandlers() {
        return handlers;
    }
}
