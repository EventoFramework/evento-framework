package org.evento.server.service.messaging;

import org.springframework.stereotype.Service;

@Service
public class MessageGatewayService {

    /*
    private static final String AGGREGATE_LOCK_PREFIX = "AGGREGATE:";
    private final HandlerService handlerService;

    private final LockRegistry lockRegistry;

    private final EventStore eventStore;

    private final BundleDeployService bundleDeployService;
    private final boolean autoscalingEnabled;
    private final String serverId;
    private final long serverVersion;
    private final PerformanceStoreService performanceStoreService;

    private final BundleService bundleService;
    private final HandlerRepository handlerRepository;

    private final MessageBus messageBus;

    public MessageGatewayService(
            HandlerService handlerService,
            LockRegistry lockRegistry,
            EventStore eventStore,
            BundleDeployService bundleDeployService,
            @Value("${evento.cluster.node.server.id}") String serverId,
            @Value("${evento.cluster.node.server.version}") long serverVersion,
            @Value("${evento.cluster.autoscaling.enabled}") boolean autoscalingEnabled,
            PerformanceStoreService performanceStoreService,
            BundleService bundleService,
            HandlerRepository handlerRepository, MessageBus messageBus) {
        this.handlerService = handlerService;
        this.lockRegistry = lockRegistry;
        this.serverId = serverId;
        this.serverVersion = serverVersion;
        this.eventStore = eventStore;
        this.bundleDeployService = bundleDeployService;
        this.performanceStoreService = performanceStoreService;
        this.bundleService = bundleService;
        this.handlerRepository = handlerRepository;
        this.autoscalingEnabled = autoscalingEnabled;
        this.messageBus = messageBus;
    }

    @PostConstruct
    public void init() throws IOException {

        new Thread(() -> {
            for (Bundle bundle : bundleService.findAllBundles()) {
                if (bundle.isAutorun() && bundle.getBucketType() != BucketType.Ephemeral)
                    bundleDeployService.waitUntilAvailable(bundle.getId());
            }
        }).start();
    }


    private void messageHandler(EventoMessage message) {
        try {
            var request = message.getBody();
            if (request instanceof ClusterNodeIsSufferingMessage m) {
                if (!this.autoscalingEnabled) return;
                var nodes = messageBus.findAllNodeAddresses(m.getBundleId());
                var bundle = bundleService.findByName(m.getBundleId());
                if (bundle.isAutorun() && nodes.size() < bundle.getMaxInstances())
                    bundleDeployService.spawn(m.getBundleId());
            } else if (request instanceof ClusterNodeIsBoredMessage m) {
                if (!this.autoscalingEnabled) return;
                var nodes = messageBus.findAllNodeAddresses(m.getBundleId());
                var bundle = bundleService.findByName(m.getBundleId());
                if (bundle.isAutorun() && nodes.size() > bundle.getMinInstances()) {
                    messageBus.sendKill(m.getNodeId());
                }
            } else if (request instanceof PerformanceServiceTimeMessage p) {
                performanceStoreService.sendServiceTimeMetricMessage(p);
            } else if (request instanceof PerformanceInvocationsMessage p) {
                performanceStoreService.sendInvocationMetricMessage(p);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private EventoResponse requestHandler(EventoRequest message) {
        try {
            var request = message.getBody();
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
                    performanceStoreService.sendServiceTimeMetric(
                            SERVER,
                            GATEWAY_COMPONENT,
                            c,
                            start
                    );
                    var invocationStart = PerformanceStoreService.now();
                    message.setBody(invocation);
                    messageBus.forward(
                            message,
                            resp -> {
                                performanceStoreService.sendServiceTimeMetric(
                                        message.getSourceBundleId(),
                                        handler.getComponent().getComponentName(),
                                        c,
                                        invocationStart
                                );

                                var cr = (DomainCommandResponseMessage) resp.getBody();
                                var esStoreStart = PerformanceStoreService.now();
                                eventStore.publishEvent(cr.getDomainEventMessage(),
                                        c.getAggregateId());
                                if (cr.getSerializedAggregateState() != null) {
                                    eventStore.saveSnapshot(
                                            c.getAggregateId(),
                                            eventStore.getLastAggregateSequenceNumber(c.getAggregateId()),
                                            cr.getSerializedAggregateState()
                                    );
                                }
                                if (cr.isAggregateDeleted()) {
                                    eventStore.deleteAggregate(c.getAggregateId());
                                }
                                performanceStoreService.sendServiceTimeMetric(
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
                                performanceStoreService.sendServiceTimeMetric(
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
                var invocationStart = PerformanceStoreService.now();
                messageBus.forward(
                        message,
                        resp -> {
                            performanceStoreService.sendServiceTimeMetric(
                                    dest.getBundleId(),
                                    handler.getComponent().getComponentName(),
                                    c,
                                    invocationStart
                            );
                            if (resp != null && ((EventMessage<?>) resp).getType() != null) {
                                var esStoreStart = PerformanceStoreService.now();
                                eventStore.publishEvent((EventMessage<?>) resp);
                                performanceStoreService.sendServiceTimeMetric(
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
                            performanceStoreService.sendServiceTimeMetric(
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
                            performanceStoreService.sendServiceTimeMetric(
                                    dest.getBundleId(),
                                    handler.getComponent().getComponentName(),
                                    q,
                                    invocationStart
                            );
                            response.sendResponse(resp);
                        },
                        error -> {
                            performanceStoreService.sendServiceTimeMetric(
                                    dest.getBundleId(),
                                    handler.getComponent().getComponentName(),
                                    q,
                                    invocationStart
                            );
                            response.sendError(error.toThrowable());
                        }
                );

            } else if (request instanceof ClusterNodeApplicationDiscoveryRequest d) {
                response.sendResponse(new BundleRegistration(
                        serverId,
                        serverVersion,
                        new ArrayList<>(),
                        new HashMap<>()
                ));
            } else if (request instanceof EventFetchRequest f) {
                try {
                    var events = f.getComponentName() == null ? eventStore.fetchEvents(
                            f.getContext(),
                            f.getLastSequenceNumber(),
                            f.getLimit()) : eventStore.fetchEvents(
                            f.getContext(),
                            f.getLastSequenceNumber(),
                            f.getLimit(), handlerRepository.findAllHandledPayloadsNameByComponentName(f.getComponentName()));
                    response.sendResponse(new EventFetchResponse(new ArrayList<>(events.stream().map(EventStoreEntry::toPublishedEvent).collect(Collectors.toList()))));
                } catch (Exception e) {
                    response.sendError(e);
                }
            } else if (request instanceof EventLastSequenceNumberRequest r) {
                response.sendResponse(new EventLastSequenceNumberResponse(eventStore.getLastEventSequenceNumber()));
            } else {
                throw new IllegalArgumentException("Missing Handler " + ((ServerHandleInvocationMessage) request).getPayload());
            }
        } catch (Exception e) {
            response.sendError(e);
        }
    }


    private void sendEventToObservers(EventMessage<?> eventMessage) {
        /*
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
    }*/

}
