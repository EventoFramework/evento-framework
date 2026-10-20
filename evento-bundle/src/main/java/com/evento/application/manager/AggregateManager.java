package com.evento.application.manager;

import com.evento.application.performance.TracingAgent;
import com.evento.application.reference.AggregateReference;
import com.evento.application.reference.AggregateStateEnvelope;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.exceptions.HandlerNotFoundException;
import com.evento.common.modeling.messaging.message.application.DecoratedDomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.DomainCommandResponseMessage;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.Message;
import com.evento.common.modeling.state.SerializedAggregateState;

import java.lang.reflect.InvocationTargetException;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The `AggregateManager` class is responsible for managing aggregates and handling domain commands.
 */
public class AggregateManager extends ReceiverComponentManager<DecoratedDomainCommandMessage, AggregateReference> {

    private static final Logger logger = LoggerFactory.getLogger(AggregateManager.class);

    /**
     * Constructs an instance of AggregateManager.
     *
     * @param bundleId The unique identifier for the bundle associated with this AggregateManager.
     * @param gatewayTelemetryProxy A function that facilitates interactions between the gateway and telemetry proxy,
     *                              taking a string and a message as input and returning a GatewayTelemetryProxy.
     * @param tracingAgent The tracing agent used to track and monitor system activities and interactions.
     * @param messageHandlerInterceptor The message interceptor used to process and manipulate incoming or outgoing messages.
     */
    public AggregateManager(String bundleId, BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy, TracingAgent tracingAgent,
                            MessageHandlerInterceptor messageHandlerInterceptor) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent, messageHandlerInterceptor);
    }

    /**
     * Parses and processes annotated aggregate classes.
     *
     * @param scan                  The classes of the bundle's package.
     * @param findInjectableObject   A function to find injectable objects.
     * @throws InvocationTargetException If there is an issue with invoking a method.
     * @throws InstantiationException    If there is an issue with instantiating a class.
     * @throws IllegalAccessException    If there is an issue with accessing a class or its members.
     */
    public void parse(ComponentScan scan, Function<Class<?>, Object> findInjectableObject) throws InvocationTargetException, InstantiationException, IllegalAccessException {

        for (Class<?> aClass : scan.annotatedWith(Aggregate.class)) {
            var aggregateReference = new AggregateReference(createComponentInstance(aClass, findInjectableObject),
                    aClass.getAnnotation(Aggregate.class).snapshotFrequency());
            for (String command : aggregateReference.getRegisteredCommands()) {
                getHandlers().put(command, aggregateReference);
                logger.info("Aggregate command handler for %s found in %s".formatted(command, aggregateReference.getRef().getClass().getName()));
            }
        }
    }


    /**
     * Handles a decorated domain command message.
     *
     * @param c The decorated domain command message to handle.
     * @return The domain command response message.
     * @throws Exception If an exception occurs during handling.
     */
    public DomainCommandResponseMessage handle(DecoratedDomainCommandMessage c) throws Throwable {
        var handler = getHandlers().get(c.getCommandMessage().getCommandName());
        if (handler == null)
            throw new HandlerNotFoundException("No handler found for %s in %s"
                    .formatted(c.getCommandMessage().getCommandName(), getBundleId()));
        var envelope = new AggregateStateEnvelope(c.getSerializedAggregateState().getAggregateState());
        var proxy = getGatewayTelemetryProxy().apply(
                handler.getComponentName(),
                c.getCommandMessage());
        return getTracingAgent().track(
                c.getCommandMessage(),
                handler.getComponentName(),
                null,
                () -> {
                    var events = handler.invokeAll(
                            c.getCommandMessage(),
                            envelope,
                            c.getEventStream(),
                            proxy,
                            proxy,
                            getMessageHandlerInterceptor()
                    );
                    var messages = new java.util.ArrayList<DomainEventMessage>(events.size());
                    for (var event : events) {
                        var m = new DomainEventMessage(event);
                        getTracingAgent().correlate(c.getCommandMessage(), m);
                        messages.add(m);
                    }
                    var em = messages.removeLast();
                    var resp = new DomainCommandResponseMessage(em,
                            (handler.getSnapshotFrequency() >= 0 & handler.getSnapshotFrequency() <= c.getEventStream().size()) ?
                                    new SerializedAggregateState<>(envelope.getAggregateState()) : null,
                            envelope.getAggregateState() != null
                                    && envelope.getAggregateState().isDeleted()
                    );
                    resp.setPrecedingEventMessages(messages);
                    proxy.sendInvocationsMetric();
                    return resp;
                });
    }
}
