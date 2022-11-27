package org.evento.server.service.messaging;

import org.evento.common.messaging.consumer.EventFetchRequest;
import org.evento.common.messaging.consumer.EventFetchResponse;
import org.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
import org.evento.common.messaging.consumer.EventLastSequenceNumberResponse;
import org.evento.common.modeling.messaging.message.application.*;
import org.evento.common.modeling.messaging.message.internal.ClusterNodeIsBoredMessage;
import org.evento.common.modeling.messaging.message.internal.ClusterNodeIsSufferingMessage;
import org.evento.common.modeling.messaging.message.internal.ServerHandleInvocationMessage;
import org.evento.common.modeling.messaging.message.bus.NodeAddress;
import org.evento.common.modeling.messaging.message.bus.ResponseSender;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.messaging.utils.AddressPicker;
import org.evento.common.messaging.utils.RoundRobinAddressPicker;
import org.evento.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryRequest;
import org.evento.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryResponse;
import org.evento.common.modeling.state.SerializedAggregateState;
import org.evento.common.performance.PerformanceMessage;
import org.evento.common.performance.PerformanceService;
import org.evento.server.service.deploy.BundleDeployService;
import org.evento.server.service.performance.PerformanceStoreService;
import org.evento.server.domain.model.BucketType;
import org.evento.server.domain.model.Bundle;
import org.evento.server.es.EventStore;
import org.evento.server.es.eventstore.EventStoreEntry;
import org.evento.server.service.BundleService;
import org.evento.server.service.HandlerService;
import org.evento.common.performance.ThreadCountAutoscalingProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static org.evento.common.performance.PerformanceService.*;

@Service
public class MessageGatewayService {
    private static final String AGGREGATE_LOCK_PREFIX = "AGGREGATE:";
    private final HandlerService handlerService;

    private final LockRegistry lockRegistry;

    private final EventStore eventStore;

    private final MessageBus messageBus;

    private final AddressPicker<?> addressPicker;

    private final BundleDeployService bundleDeployService;

    private final ThreadCountAutoscalingProtocol threadCountAutoscalingProtocol;
    private final boolean autoscalingEnabled;
    private final String serverId;
    private final long serverVersion;

    private final PerformanceStoreService performanceStoreService;
    private final PerformanceService performanceService;

    private final BundleService bundleService;
    private final int maxInstances;
    private final int minInstances;
    private final boolean autorun;

    public MessageGatewayService(
            HandlerService handlerService,
            LockRegistry lockRegistry,
            EventStore eventStore,
            MessageBus messageBus,
            BundleDeployService bundleDeployService,
            @Value("${evento.cluster.node.server.id}") String serverId,
            @Value("${evento.cluster.node.server.version}") long serverVersion,
            @Value("${evento.cluster.autoscaling.max.threads}") int maxThreads,
            @Value("${evento.cluster.autoscaling.max.overflow}") int maxOverflow,
            @Value("${evento.cluster.autoscaling.min.threads}") int minThreads,
            @Value("${evento.cluster.autoscaling.max.underflow}") int maxUnderflow,
            @Value("${evento.bundle.autorun:false}") boolean autorun,
            @Value("${evento.bundle.instances.min:0}") int minInstances,
            @Value("${evento.bundle.instances.max:64}") int maxInstances,
            @Value("${evento.cluster.autoscaling.enabled}") boolean autoscalingEnabled,
            PerformanceStoreService performanceStoreService, BundleService bundleService) {
        this.handlerService = handlerService;
        this.lockRegistry = lockRegistry;
        this.serverId = serverId;
        this.serverVersion = serverVersion;
        this.eventStore = eventStore;
        this.messageBus = messageBus;
        this.addressPicker = new RoundRobinAddressPicker(messageBus);
        this.bundleDeployService = bundleDeployService;
        this.performanceStoreService = performanceStoreService;
        this.bundleService = bundleService;
        this.performanceService = new PerformanceService(messageBus, serverId);
        this.maxInstances = maxInstances;
        this.minInstances = minInstances;
        this.autorun = autorun;

        this.threadCountAutoscalingProtocol = new ThreadCountAutoscalingProtocol(
                this.serverId,
                this.serverId,
                messageBus,
                maxThreads,
                minThreads,
                maxOverflow,
                maxUnderflow
        );
        this.autoscalingEnabled = autoscalingEnabled;
        messageBus.setRequestReceiver(this::requestHandler);
        messageBus.setMessageReceiver(this::messageHandler);
    }

    @PostConstruct
    public void init() {
        new Thread(() -> {
            for (Bundle bundle : bundleService.findAllBundles()) {
                if (bundle.isAutorun() && bundle.getBucketType() != BucketType.Ephemeral)
                    bundleDeployService.waitUntilAvailable(bundle.getId());
            }
        }).start();
    }

    private void messageHandler(NodeAddress source, Serializable request) {
        try {
            if (this.autoscalingEnabled) {
                if (request instanceof ClusterNodeIsSufferingMessage m) {
                    var nodes = messageBus.findAllNodeAddresses(m.getBundleId());
                    var bundle = bundleService.findByName(m.getBundleId());
                    if (nodes.size() < bundle.getMaxInstances())
                        bundleDeployService.spawn(m.getBundleId());
                    return;
                } else if (request instanceof ClusterNodeIsBoredMessage m) {
                    var nodes = messageBus.findAllNodeAddresses(m.getBundleId());
                    var bundle = bundleService.findByName(m.getBundleId());
                    if (nodes.size() > bundle.getMinInstances()) {
                        messageBus.sendKill(m.getNodeId());
                    }
                    return;
                }
            }
            if (request instanceof PerformanceMessage p) {
                performanceStoreService.savePerformance(
                        p.getBundle(),
                        p.getComponent(),
                        p.getAction(),
                        p.getDuration()
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestHandler(NodeAddress source, Serializable request, ResponseSender response) {
        try {
            this.threadCountAutoscalingProtocol.arrival();
            if (request instanceof DomainCommandMessage c) {
                var handler = handlerService.findByPayloadName(c.getCommandName());
                bundleDeployService.waitUntilAvailable(handler.getBundle().getId());
                var start = PerformanceStoreService.now();

                var lock = lockRegistry.obtain(AGGREGATE_LOCK_PREFIX + c.getAggregateId());
                lock.lock();
                var semaphore = new Semaphore(0);

                var dest = addressPicker.pickNodeAddress(handler.getBundle().getId());

                try {
                    var invocation = new DecoratedDomainCommandMessage();
                    invocation.setCommandMessage(c);

                    var snapshot = eventStore.fetchSnapshot(c.getAggregateId());
                    if (snapshot == null) {
                        invocation.setEventStream(eventStore.fetchAggregateStory(c.getAggregateId())
                                .stream().map(EventStoreEntry::getEventMessage)
                                .map(e -> ((DomainEventMessage) e)).collect(Collectors.toList()));
                        invocation.setSerializedAggregateState(new SerializedAggregateState<>(null));
                    } else {
                        invocation.setEventStream(eventStore.fetchAggregateStory(c.getAggregateId(),
                                        snapshot.getAggregateSequenceNumber())
                                .stream().map(EventStoreEntry::getEventMessage)
                                .map(e -> ((DomainEventMessage) e)).collect(Collectors.toList()));
                        invocation.setSerializedAggregateState(snapshot.getAggregateState());
                    }
                    performanceService.sendPerformances(
                            SERVER,
                            GATEWAY_COMPONENT,
                            c.getCommandName(),
                            start
                    );
                    var invocationStart = PerformanceStoreService.now();
                    messageBus.request(
                            dest,
                            invocation,
                            resp -> {
                                performanceService.sendPerformances(
                                        dest.getBundleId(),
                                        handler.getComponentName(),
                                        c.getCommandName(),
                                        invocationStart
                                );

                                var cr = (DomainCommandResponseMessage) resp;
                                var esStoreStart = PerformanceStoreService.now();
                                var aggregateSequenceNumber = eventStore.publishEvent(cr.getDomainEventMessage(), c.getAggregateId());
                                if (cr.getSerializedAggregateState() != null) {
                                    eventStore.saveSnapshot(
                                            c.getAggregateId(),
                                            aggregateSequenceNumber,
                                            cr.getSerializedAggregateState()
                                    );
                                }
                                performanceService.sendPerformances(
                                        EVENT_STORE,
                                        EVENT_STORE_COMPONENT,
                                        cr.getDomainEventMessage().getEventName(),
                                        esStoreStart
                                );
                                response.sendResponse(resp);
                                semaphore.release();

                            },
                            error -> {
                                performanceService.sendPerformances(
                                        dest.getBundleId(),
                                        handler.getComponentName(),
                                        c.getCommandName(),
                                        invocationStart
                                );
                                response.sendError(error.toThrowable());
                                semaphore.release();

                            }

                    );
                } catch (Exception e) {
                    semaphore.release();
                    throw e;
                } finally {
                    semaphore.acquire();
                    lock.unlock();
                }

            } else if (request instanceof ServiceCommandMessage c) {
                var handler = handlerService.findByPayloadName(c.getCommandName());
                bundleDeployService.waitUntilAvailable(handler.getBundle().getId());
                var dest = addressPicker.pickNodeAddress(handler.getBundle().getId());
                var invocationStart = PerformanceStoreService.now();
                messageBus.request(
                        dest,
                        c,
                        resp -> {
                            performanceService.sendPerformances(
                                    dest.getBundleId(),
                                    handler.getComponentName(),
                                    c.getCommandName(),
                                    invocationStart
                            );
                            if (resp != null && ((EventMessage<?>) resp).getType() != null) {
                                var esStoreStart = PerformanceStoreService.now();
                                eventStore.publishEvent((EventMessage<?>) resp);
                                performanceService.sendPerformances(
                                        EVENT_STORE,
                                        EVENT_STORE_COMPONENT,
                                        ((EventMessage<?>) resp).getEventName(),
                                        esStoreStart
                                );
                            }
                            response.sendResponse(resp);

                        },
                        error -> {
                            response.sendError(error.toThrowable());
                            performanceService.sendPerformances(
                                    dest.getBundleId(),
                                    handler.getComponentName(),
                                    c.getCommandName(),
                                    invocationStart
                            );
                        }

                );

            }
            else if (request instanceof QueryMessage<?> q) {
                var handler = handlerService.findByPayloadName(q.getQueryName());
                bundleDeployService.waitUntilAvailable(handler.getBundle().getId());
                var dest = addressPicker.pickNodeAddress(handler.getBundle().getId());
                var invocationStart = PerformanceStoreService.now();
                messageBus.request(
                        dest,
                        q,
                        resp -> {
                            performanceService.sendPerformances(
                                    dest.getBundleId(),
                                    handler.getComponentName(),
                                    q.getQueryName(),
                                    invocationStart
                            );
                            response.sendResponse(resp);
                        },
                        error -> {
                            performanceService.sendPerformances(
                                    dest.getBundleId(),
                                    handler.getComponentName(),
                                    q.getQueryName(),
                                    invocationStart
                            );
                            response.sendError(error.toThrowable());
                        }
                );

            }
            else if (request instanceof ClusterNodeApplicationDiscoveryRequest d) {
                response.sendResponse(new ClusterNodeApplicationDiscoveryResponse(
                        serverId,
                        serverVersion,
                        autorun,
                        minInstances,
                        maxInstances,
                        new ArrayList<>()
                ));
            }
            else if (request instanceof EventFetchRequest f) {
                var events = eventStore.fetchEvents(f.getLastSequenceNumber(), f.getLimit());
                response.sendResponse(new EventFetchResponse(new ArrayList<>(events.stream().map(EventStoreEntry::toPublishedEvent).collect(Collectors.toList()))));
            }
            else if (request instanceof EventLastSequenceNumberRequest r){
                response.sendResponse(new EventLastSequenceNumberResponse(eventStore.getLastEventSequenceNumber()));
            }
            else {
                throw new IllegalArgumentException("Missing Handler " + ((ServerHandleInvocationMessage) request).getPayload());
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(e);
        } finally {
            this.threadCountAutoscalingProtocol.departure();
        }
    }

}
