package org.evento.server.service.messaging;

import org.evento.common.messaging.consumer.EventFetchRequest;
import org.evento.common.messaging.consumer.EventFetchResponse;
import org.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
import org.evento.common.messaging.consumer.EventLastSequenceNumberResponse;
import org.evento.common.modeling.bundle.types.ComponentType;
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
import org.evento.common.performance.PerformanceInvocationsMessage;
import org.evento.common.performance.PerformanceServiceTimeMessage;
import org.evento.common.performance.PerformanceService;
import org.evento.server.domain.model.Handler;
import org.evento.server.domain.repository.HandlerRepository;
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
import java.util.HashMap;
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
    private final HandlerRepository handlerRepository;

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
            @Value("${evento.cluster.autoscaling.enabled}") boolean autoscalingEnabled,
            PerformanceStoreService performanceStoreService, BundleService bundleService,
            HandlerRepository handlerRepository) {
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
        this.handlerRepository = handlerRepository;
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
            if (request instanceof PerformanceServiceTimeMessage p) {
                performanceStoreService.saveServiceTimePerformance(
                        p.getBundle(),
                        p.getComponent(),
                        p.getAction(),
                        p.getDuration()
                );
            } else if (request instanceof PerformanceInvocationsMessage p)
            {
                performanceStoreService.saveInvocationsPerformance(
                        p.getBundle(),
                        p.getComponent(),
                        p.getAction(),
                        p.getInvocations()                );
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
                bundleDeployService.waitUntilAvailable(handler.getComponent().getBundle().getId());
                var start = PerformanceStoreService.now();

                var lock = lockRegistry.obtain(AGGREGATE_LOCK_PREFIX + c.getAggregateId());
                lock.lock();
                var semaphore = new Semaphore(0);

                var dest = addressPicker.pickNodeAddress(handler.getComponent().getBundle().getId());

                try {
                    var invocation = new DecoratedDomainCommandMessage();
                    invocation.setCommandMessage(c);

                    var snapshot = eventStore.fetchSnapshot(c.getAggregateId());
                    if (snapshot == null) {
                        invocation.setEventStream(eventStore.fetchAggregateState(c.getAggregateId())
                                .stream().map(EventStoreEntry::getEventMessage)
                                .map(e -> ((DomainEventMessage) e)).collect(Collectors.toList()));
                        invocation.setSerializedAggregateState(new SerializedAggregateState<>(null));
                    } else {
                        invocation.setEventStream(eventStore.fetchAggregateState(c.getAggregateId(),
                                        snapshot.getEventSequenceNumber())
                                .stream().map(EventStoreEntry::getEventMessage)
                                .map(e -> ((DomainEventMessage) e)).collect(Collectors.toList()));
                        invocation.setSerializedAggregateState(snapshot.getAggregateState());
                    }
                    performanceService.sendServiceTimeMetric(
                            SERVER,
                            GATEWAY_COMPONENT,
                            c,
                            start
                    );
                    var invocationStart = PerformanceStoreService.now();
                    messageBus.request(
                            dest,
                            invocation,
                            resp -> {
                                performanceService.sendServiceTimeMetric(
                                        dest.getBundleId(),
                                        handler.getComponent().getComponentName(),
                                        c,
                                        invocationStart
                                );

                                var cr = (DomainCommandResponseMessage) resp;
                                var esStoreStart = PerformanceStoreService.now();
                                eventStore.publishEvent(cr.getDomainEventMessage(), c.getAggregateId());
                                if (cr.getSerializedAggregateState() != null) {
                                    eventStore.saveSnapshot(
                                            c.getAggregateId(),
                                            eventStore.getLastAggregateSequenceNumber(c.getAggregateId()),
                                            cr.getSerializedAggregateState()
                                    );
                                }
                                performanceService.sendServiceTimeMetric(
                                        EVENT_STORE,
                                        EVENT_STORE_COMPONENT,
                                        cr.getDomainEventMessage(),
                                        esStoreStart
                                );
                                response.sendResponse(cr.getDomainEventMessage());
                                semaphore.release();
                                sendEventToObservers(cr.getDomainEventMessage());

                            },
                            error -> {
                                performanceService.sendServiceTimeMetric(
                                        dest.getBundleId(),
                                        handler.getComponent().getComponentName(),
                                        c,
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
                bundleDeployService.waitUntilAvailable(handler.getComponent().getBundle().getId());
                var dest = addressPicker.pickNodeAddress(handler.getComponent().getBundle().getId());
                var invocationStart = PerformanceStoreService.now();
                messageBus.request(
                        dest,
                        c,
                        resp -> {
                            performanceService.sendServiceTimeMetric(
                                    dest.getBundleId(),
                                    handler.getComponent().getComponentName(),
                                    c,
                                    invocationStart
                            );
                            if (resp != null && ((EventMessage<?>) resp).getType() != null) {
                                var esStoreStart = PerformanceStoreService.now();
                                eventStore.publishEvent((EventMessage<?>) resp);
                                performanceService.sendServiceTimeMetric(
                                        EVENT_STORE,
                                        EVENT_STORE_COMPONENT,
                                        ((EventMessage<?>) resp),
                                        esStoreStart
                                );
                            }
                            response.sendResponse(resp);
                            if (resp != null && ((EventMessage<?>) resp).getType() != null) {
                                sendEventToObservers((EventMessage<?>) resp);
                            }
                        },
                        error -> {
                            response.sendError(error.toThrowable());
                            performanceService.sendServiceTimeMetric(
                                    dest.getBundleId(),
                                    handler.getComponent().getComponentName(),
                                    c,
                                    invocationStart
                            );
                        }

                );

            } else if (request instanceof QueryMessage<?> q) {
                var handler = handlerService.findByPayloadName(q.getQueryName());
                bundleDeployService.waitUntilAvailable(handler.getComponent().getBundle().getId());
                var dest = addressPicker.pickNodeAddress(handler.getComponent().getBundle().getId());
                var invocationStart = PerformanceStoreService.now();
                messageBus.request(
                        dest,
                        q,
                        resp -> {
                            performanceService.sendServiceTimeMetric(
                                    dest.getBundleId(),
                                    handler.getComponent().getComponentName(),
                                    q,
                                    invocationStart
                            );
                            response.sendResponse(resp);
                        },
                        error -> {
                            performanceService.sendServiceTimeMetric(
                                    dest.getBundleId(),
                                    handler.getComponent().getComponentName(),
                                    q,
                                    invocationStart
                            );
                            response.sendError(error.toThrowable());
                        }
                );

            } else if (request instanceof ClusterNodeApplicationDiscoveryRequest d) {
                response.sendResponse(new ClusterNodeApplicationDiscoveryResponse(
                        serverId,
                        serverVersion,
                        new ArrayList<>(),
                        new HashMap<>()
                ));
            } else if (request instanceof EventFetchRequest f) {
                try {
                    var events = f.getComponentName() == null ? eventStore.fetchEvents(
                            f.getLastSequenceNumber(),
                            f.getLimit()) : eventStore.fetchEvents(
                            f.getLastSequenceNumber(),
                            f.getLimit(), handlerRepository.findAllHandledPayloadsNameByComponentName(f.getComponentName()));
                    response.sendResponse(new EventFetchResponse(new ArrayList<>(events.stream().map(EventStoreEntry::toPublishedEvent).collect(Collectors.toList()))));
                }catch (Exception e){
                    response.sendError(e);
                }
            } else if (request instanceof EventLastSequenceNumberRequest r) {
                response.sendResponse(new EventLastSequenceNumberResponse(eventStore.getLastEventSequenceNumber()));
            } else {
                throw new IllegalArgumentException("Missing Handler " + ((ServerHandleInvocationMessage) request).getPayload());
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(e);
        } finally {
            this.threadCountAutoscalingProtocol.departure();
        }
    }

    private void sendEventToObservers(EventMessage<?> eventMessage) {
        for (Handler h : handlerService.findAllByPayloadName(eventMessage.getEventName())) {
            if (h.getComponent().getComponentType() == ComponentType.Observer) {
                try {
                    bundleDeployService.waitUntilAvailable(h.getComponent().getBundle().getId());
                    var d = addressPicker.pickNodeAddress(h.getComponent().getBundle().getId());
                    messageBus.cast(d, eventMessage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
