package org.evento.application.manager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.AggregateReference;
import org.evento.application.reference.AggregateStateEnvelope;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.modeling.annotations.component.Aggregate;
import org.evento.common.modeling.exceptions.HandlerNotFoundException;
import org.evento.common.modeling.messaging.message.application.DecoratedDomainCommandMessage;
import org.evento.common.modeling.messaging.message.application.DomainCommandResponseMessage;
import org.evento.common.modeling.messaging.message.application.DomainEventMessage;
import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.modeling.state.SerializedAggregateState;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.function.BiFunction;
import java.util.function.Function;

public class AggregateManager extends ReceiverComponentManager<DecoratedDomainCommandMessage, AggregateReference>{

    private static final Logger logger = LogManager.getLogger(AggregateManager.class);


    public AggregateManager(String bundleId,BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy, TracingAgent tracingAgent) {
        super(bundleId, gatewayTelemetryProxy, tracingAgent);
    }

    public void parse(Reflections reflections, Function<Class<?>, Object> findInjectableObject) throws InvocationTargetException, InstantiationException, IllegalAccessException {

        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Aggregate.class)) {
            var aggregateReference = new AggregateReference(createComponentInstance(aClass, findInjectableObject),
                    aClass.getAnnotation(Aggregate.class).snapshotFrequency());
            for (String command : aggregateReference.getRegisteredCommands()) {
                getHandlers().put(command, aggregateReference);
                logger.info("Aggregate command handler for %s found in %s".formatted(command, aggregateReference.getRef().getClass().getName()));
            }
        }
    }


    public void handle(
            DecoratedDomainCommandMessage c,
            MessageBus.MessageBusResponseSender response
           ) throws Throwable {
        var handler = getHandlers().get(c.getCommandMessage().getCommandName());
        if (handler == null)
            throw new HandlerNotFoundException("No handler found for %s in %s"
                    .formatted(c.getCommandMessage().getCommandName(), getBundleId()));
        var envelope = new AggregateStateEnvelope(c.getSerializedAggregateState().getAggregateState());
        var proxy = getGatewayTelemetryProxy().apply(
                handler.getComponentName(),
                c.getCommandMessage());
        getTracingAgent().track(
                c.getCommandMessage(),
                handler.getComponentName(),
                null,
                () -> {
                    var event = handler.invoke(
                            c.getCommandMessage(),
                            envelope,
                            c.getEventStream(),
                            proxy,
                            proxy
                    );
                    var em = new DomainEventMessage(event);
                    getTracingAgent().correlate(c.getCommandMessage(), em);
                    response.sendResponse(
                            new DomainCommandResponseMessage(em,
                                    (handler.getSnapshotFrequency() >= 0 & handler.getSnapshotFrequency() <= c.getEventStream().size()) ?
                                            new SerializedAggregateState<>(envelope.getAggregateState()) : null,
                                    envelope.getAggregateState() != null
                                            && envelope.getAggregateState().isDeleted()
                            )
                    );
                    proxy.sendInvocationsMetric();
                    return null;
                });
    }
}
