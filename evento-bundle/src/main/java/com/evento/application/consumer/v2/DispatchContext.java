package com.evento.application.consumer.v2;

import com.evento.application.manager.MessageHandlerInterceptor;
import com.evento.application.performance.TracingAgent;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.modeling.messaging.message.application.Message;

import java.util.function.BiFunction;

public record DispatchContext(
        TracingAgent tracingAgent,
        BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy,
        MessageHandlerInterceptor messageHandlerInterceptor
) {}
