package org.evento.application.manager;

import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.ProjectorReference;
import org.evento.application.reference.Reference;
import org.evento.common.messaging.consumer.ConsumerStateStore;
import org.evento.common.modeling.messaging.message.application.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public abstract class ConsumerComponentManager<R extends Reference> extends ComponentManager {


    private final HashMap<String, HashMap<String, R>> handlers = new HashMap<>();
    private final List<R> references = new ArrayList<>();

    private final Supplier<Boolean> isShuttingDown;

    private final ConsumerStateStore consumerStateStore;


    private final int sssFetchSize;

    private final int sssFetchDelay;

    protected ConsumerComponentManager(
            String bundleId,
            BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy,
            TracingAgent tracingAgent, Supplier<Boolean> isShuttingDown, ConsumerStateStore consumerStateStore, int sssFetchSize, int sssFetchDelay) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent);
        this.isShuttingDown = isShuttingDown;
        this.consumerStateStore = consumerStateStore;
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;
    }

    public HashMap<String, HashMap<String, R>> getHandlers() {
        return handlers;
    }

    public Supplier<Boolean> getIsShuttingDown() {
        return isShuttingDown;
    }

    public ConsumerStateStore getConsumerStateStore() {
        return consumerStateStore;
    }

    public int getSssFetchSize() {
        return sssFetchSize;
    }

    public int getSssFetchDelay() {
        return sssFetchDelay;
    }

    public List<R> getReferences() {
        return references;
    }
}
