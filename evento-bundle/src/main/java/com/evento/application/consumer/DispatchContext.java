package com.evento.application.consumer;

import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.application.performance.TracingAgent;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.Message;

import java.util.function.BiFunction;

public record DispatchContext(
        TracingAgent tracingAgent,
        BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy,
        MessageHandlerInterceptor messageHandlerInterceptor
) {

    /**
     * The gateway a consumer handler gets for one event: commands it sends without an explicit
     * idempotency key get one derived from {@code (consumerId, event sequence number, ordinal)},
     * so a redelivered event re-issues the same keys and the broker replays the stored events
     * instead of executing the commands again (G5). {@code null} when the factory yields none
     * (engine unit tests).
     */
    public GatewayTelemetryProxy consumerGateway(String componentName, PublishedEvent event, String consumerId) {
        var proxy = gatewayTelemetryProxy.apply(componentName, event.getEventMessage());
        return proxy == null ? null : proxy.withDerivedIdempotencyKeys(consumerId, event.getEventSequenceNumber());
    }
}
