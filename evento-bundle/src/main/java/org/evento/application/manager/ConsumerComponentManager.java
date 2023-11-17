package org.evento.application.manager;

import lombok.Getter;
import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.Reference;
import org.evento.common.modeling.messaging.message.application.Message;

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
     * Constructs a `ConsumerComponentManager`.
     *
     * @param bundleId              The bundle identifier.
     * @param gatewayTelemetryProxy A function to create a `GatewayTelemetryProxy`.
     * @param tracingAgent          The tracing agent for telemetry.
     * @param isShuttingDown        A supplier to check if the application is shutting down.
     * @param sssFetchSize          Size of events to fetch from the state store.
     * @param sssFetchDelay         Delay between fetching events from the state store.
     */
    protected ConsumerComponentManager(
            String bundleId,
            BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy,
            TracingAgent tracingAgent, Supplier<Boolean> isShuttingDown, int sssFetchSize, int sssFetchDelay) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent);
        this.isShuttingDown = isShuttingDown;
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;
    }

}
