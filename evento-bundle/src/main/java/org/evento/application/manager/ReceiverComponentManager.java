package org.evento.application.manager;

import lombok.Getter;
import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.Reference;
import org.evento.common.modeling.messaging.message.application.Message;

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
     * Constructs a `ReceiverComponentManager`.
     *
     * @param bundleId              The bundle identifier.
     * @param gatewayTelemetryProxy A function to create a `GatewayTelemetryProxy`.
     * @param tracingAgent          The tracing agent for telemetry.
     */
    protected ReceiverComponentManager(
            String bundleId,
            BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy,
            TracingAgent tracingAgent) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent);
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
