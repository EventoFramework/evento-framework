package com.evento.application.manager;

import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.Reference;
import lombok.Getter;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.modeling.messaging.message.application.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The `ConsumerComponentManager` class serves as an abstract base class for managing consumer components that handle messages.
 *
 * @param <R> The type of references to components.
 */
@Getter
public abstract class ConsumerComponentManager<R extends Reference> extends ComponentManager {

    // Map to store handlers associated with this manager, organized by event name and component name
    private final HashMap<String, HashMap<String, R>> handlers = new HashMap<>();

    // List to store references to components
    private final List<R> references = new ArrayList<>();

    // Supplier to check if the application is shutting down
    private final Supplier<Boolean> isShuttingDown;

    // Size of events to fetch from the state store
    private final int sssFetchSize;

    // Delay between fetching events from the state store
    private final int sssFetchDelay;

    /**
     * Constructs a `ConsumerComponentManager` with the specified parameters.
     *
     * @param bundleId                 The ID of the bundle associated with this consumer component manager.
     * @param gatewayTelemetryProxy    A function that provides a telemetry proxy for gateway operations,
     *                                  based on the bundle ID and a message.
     * @param tracingAgent             The tracing agent to use for distributed tracing functionalities.
     * @param isShuttingDown           A supplier that indicates whether the application is shutting down.
     * @param sssFetchSize             The size of events to fetch from the state store.
     * @param sssFetchDelay            The delay, in milliseconds, between fetching events from the state store.
     * @param messageHandlerInterceptor       The interceptor for managing logic before or after message handling.
     */
    protected ConsumerComponentManager(
            String bundleId,
            BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy,
            TracingAgent tracingAgent, Supplier<Boolean> isShuttingDown, int sssFetchSize, int sssFetchDelay,
            MessageHandlerInterceptor messageHandlerInterceptor) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, messageHandlerInterceptor);
        this.isShuttingDown = isShuttingDown;
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;
    }

}
