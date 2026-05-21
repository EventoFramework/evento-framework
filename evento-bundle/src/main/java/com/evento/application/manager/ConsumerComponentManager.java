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

@Getter
public abstract class ConsumerComponentManager<R extends Reference> extends ComponentManager {

    private final HashMap<String, HashMap<String, R>> handlers = new HashMap<>();
    private final List<R> references = new ArrayList<>();
    private final int sssFetchSize;
    private final int sssFetchDelay;

    protected ConsumerComponentManager(
            String bundleId,
            BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy,
            TracingAgent tracingAgent, int sssFetchSize, int sssFetchDelay,
            MessageHandlerInterceptor messageHandlerInterceptor) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, messageHandlerInterceptor);
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;
    }
}
