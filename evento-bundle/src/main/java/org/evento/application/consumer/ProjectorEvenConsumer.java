package org.evento.application.consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.application.performance.TracingAgent;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.reference.ProjectorReference;
import org.evento.common.messaging.consumer.ConsumerStateStore;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.common.modeling.messaging.message.application.Message;
import org.evento.common.utils.ProjectorStatus;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ProjectorEvenConsumer implements Runnable {

    private final Logger logger = LogManager.getLogger(ProjectorEvenConsumer.class);

    private final String bundleId;
    private final String projectorName;
    private final int projectorVersion;
    private final String context;

    private final Supplier<Boolean> isShuttingDown;

    private final ConsumerStateStore consumerStateStore;

    private final HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers;

    private final TracingAgent tracingAgent;
    private final BiFunction<String, Message<?>, GatewayTelemetryProxy> gatewayTelemetryProxy;

    private final int sssFetchSize;

    private final int sssFetchDelay;

    private final AtomicInteger alignmentCounter;

    private final Runnable onHeadReached;

    public ProjectorEvenConsumer(String bundleId,
                                 String projectorName, int projectorVersion,
                                 String context, Supplier<Boolean> isShuttingDown,
                                 ConsumerStateStore consumerStateStore,
                                 HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers,
                                 TracingAgent tracingAgent, BiFunction<String, Message<?>,
            GatewayTelemetryProxy> gatewayTelemetryProxy, int sssFetchSize,
                                 int sssFetchDelay, AtomicInteger alignmentCounter,
                                 Runnable onHeadReached) {
        this.bundleId = bundleId;
        this.projectorName = projectorName;
        this.projectorVersion = projectorVersion;
        this.context = context;
        this.isShuttingDown = isShuttingDown;
        this.consumerStateStore = consumerStateStore;
        this.projectorMessageHandlers = projectorMessageHandlers;
        this.tracingAgent = tracingAgent;
        this.gatewayTelemetryProxy = gatewayTelemetryProxy;
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;
        this.alignmentCounter = alignmentCounter;
        this.onHeadReached = onHeadReached;
    }

    @Override
    public void run() {
        var ps = new ProjectorStatus();
        ps.setHeadReached(false);
        var consumerId = bundleId + "_" + projectorName + "_" + projectorVersion + "_" + context;
        while (!isShuttingDown.get()) {
            var hasError = false;
            var consumedEventCount = 0;
            try {
                consumedEventCount = consumerStateStore.consumeEventsForProjector(
                        consumerId,
                        projectorName,
                        context,
                        publishedEvent -> {
                            var handlers = projectorMessageHandlers
                                    .get(publishedEvent.getEventName());
                            if (handlers == null) return;

                            var handler = handlers.getOrDefault(projectorName, null);
                            if (handler == null) return;
                            var proxy = gatewayTelemetryProxy.apply(handler.getComponentName(),
                                    publishedEvent.getEventMessage());
                            tracingAgent.track(publishedEvent.getEventMessage(), handler.getComponentName(),
                                    null,
                                    () -> {
                                        handler.invoke(
                                                publishedEvent.getEventMessage(),
                                                proxy,
                                                proxy,
                                                ps
                                        );
                                        proxy.sendInvocationsMetric();
                                        return null;
                                    });

                        }, sssFetchSize);
            } catch (Throwable e) {
                logger.error("Error on projector consumer: " + consumerId, e);
                hasError = true;
            }
            if (sssFetchSize - consumedEventCount > 10) {
                try {
                    Thread.sleep(hasError ? sssFetchDelay : sssFetchSize - consumedEventCount);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (!hasError && !ps.isHeadReached() && consumedEventCount >= 0 && consumedEventCount < sssFetchSize) {
                ps.setHeadReached(true);
                var aligned = alignmentCounter.decrementAndGet();
                if (aligned == 0) {
                    onHeadReached.run();
                }
            }
        }
    }
}
