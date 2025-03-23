package com.evento.application.manager;

import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.Reference;
import lombok.Getter;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.modeling.messaging.message.application.Message;

import java.io.Serializable;
import java.util.HashMap;
import java.util.function.BiFunction;

/**
 * The `ReceiverComponentManager` class serves as an abstract base class for managing components that handle messages.
 *
 * @param <M> The type of messages that this manager handles.
 * @param <R> The type of references to components.
 */
@Getter
public abstract class ReceiverComponentManager<M extends Serializable, R extends Reference> extends ComponentManager {

    // Map to store handlers associated with this manager
    private final HashMap<String, R> handlers = new HashMap<>();

    /**
     * Constructs a new instance of `ReceiverComponentManager` with the specified parameters.
     *
     * @param bundleId The unique identifier of the bundle associated with this manager.
     * @param gatewayTelemetryProxy A bi-function that generates a `GatewayTelemetryProxy` instance for telemetry data,
     *                               based on the bundle identifier and a message.
     * @param tracingAgent An instance of the `TracingAgent` used for distributed tracing and context propagation.
     * @param messageHandlerInterceptor An interceptor used to process or modify messages before handling them.
     */
    protected ReceiverComponentManager(
            String bundleId,
            BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy,
            TracingAgent tracingAgent,
            MessageHandlerInterceptor messageHandlerInterceptor) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, messageHandlerInterceptor);
    }

    /**
     * Abstract method to handle messages of type `M`.
     *
     * @param c The message to be handled.
     * @return A `Serializable` response from the message handling process.
     * @throws Throwable If there is an error during message handling.
     */
    abstract public Serializable handle(
            M c) throws Throwable;


}
