package org.evento.server.bus;

import org.evento.common.messaging.consumer.EventFetchRequest;
import org.evento.common.messaging.consumer.EventFetchResponse;
import org.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
import org.evento.common.messaging.consumer.EventLastSequenceNumberResponse;
import org.evento.common.modeling.bundle.types.ComponentType;
import org.evento.common.modeling.exceptions.ExceptionWrapper;
import org.evento.common.modeling.messaging.message.application.*;
import org.evento.common.modeling.messaging.message.internal.*;
import org.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;
import org.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import org.evento.common.utils.Sleep;
import org.evento.server.domain.model.core.BucketType;
import org.evento.server.domain.model.core.Bundle;
import org.evento.server.es.EventStore;
import org.evento.server.es.eventstore.EventStoreEntry;
import org.evento.server.service.BundleService;
import org.evento.server.service.HandlerService;
import org.evento.server.service.deploy.BundleDeployService;
import org.evento.server.service.performance.PerformanceStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.evento.common.performance.PerformanceService.*;

@Component
public class MessageBus {


    private final Logger logger = LoggerFactory.getLogger(MessageBus.class);
    private final int socketPort;

    private final HashMap<NodeAddress, ObjectOutputStream> view = new HashMap<>();
    private final HashMap<NodeAddress, BundleRegistration> registrations = new HashMap<>();
    private final Set<NodeAddress> availableView = new HashSet<>();
    private final Map<String, Set<NodeAddress>> handlers = new HashMap<>();

    private final BundleDeployService bundleDeployService;

    private static final String AGGREGATE_LOCK_PREFIX = "AGGREGATE:";
    private static final String SERVICE_LOCK_PREFIX = "SERVICE:";
    private final HandlerService handlerService;

    private final EventStore eventStore;


    private final ConcurrentHashMap<String, Semaphore> semaphoreMap = new ConcurrentHashMap<>();


    private final PerformanceStoreService performanceStoreService;

    private final BundleService bundleService;

    private final Map<String, Consumer<EventoResponse>> correlations = new ConcurrentHashMap<>();
    private boolean isShuttingDown = false;

    public MessageBus(
            @Value("${socket.port}") int socketPort, BundleDeployService bundleDeployService, HandlerService handlerService, EventStore eventStore, PerformanceStoreService performanceStoreService, BundleService bundleService) {
        this.socketPort = socketPort;
        this.bundleDeployService = bundleDeployService;
        this.handlerService = handlerService;
        this.eventStore = eventStore;
        this.performanceStoreService = performanceStoreService;
        this.bundleService = bundleService;
    }

    @PostConstruct
    public void init() {

        new Thread(() -> {
            for (Bundle bundle : bundleService.findAllBundles()) {
                if (bundle.isAutorun() && bundle.getBucketType() != BucketType.Ephemeral)
                    waitUntilAvailable(bundle);
            }
        }).start();

        new Thread(() -> {
            try(ServerSocket server = new ServerSocket(socketPort)) {
                while (true) {
                    var conn = server.accept();
                    logger.info("New connection: " + conn.getInetAddress());
                    new Thread(() -> {
                        NodeAddress address = null;
                        try {
                            try {
                                var in = new ObjectInputStream(conn.getInputStream());
                                var out = new ObjectOutputStream(conn.getOutputStream());
                                final var a = join((BundleRegistration) in.readObject(), out);
                                synchronized (out) {
                                    out.writeObject(true);
                                }
                                address = a;

                                while (true) {
                                    var message = in.readObject();
                                    new Thread(() -> {
                                        try {
                                            if (message instanceof DisableMessage) {
                                                disable(a);
                                            } else if (message instanceof EnableMessage) {
                                                enable(a);
                                            } else if (message instanceof EventoRequest r) {
                                                handleRequest(r, resp -> {
                                                    try {
                                                        synchronized (out) {
                                                            out.writeObject(resp);
                                                        }
                                                    } catch (IOException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                });
                                            } else if (message instanceof EventoResponse r) {
                                                var c = correlations.get(r.getCorrelationId());
                                                correlations.remove(r.getCorrelationId());
                                                c.accept(r);
                                            } else if (message instanceof EventoMessage m) {
                                                handleMessage(m);
                                            }
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    }).start();
                                }
                            } catch (Exception e) {
                                for (Map.Entry<String, Consumer<EventoResponse>> ek : correlations.entrySet()) {
                                    var resp = new EventoResponse();
                                    resp.setCorrelationId(ek.getKey());
                                    resp.setBody(new ExceptionWrapper(e));
                                    try {
                                        ek.getValue().accept(resp);
                                    } catch (Exception ex) {
                                        logger.error("Error during correlation fail management", ex);
                                    }
                                }
                                try {
                                    if (!conn.isClosed())
                                        conn.close();
                                } catch (IOException ex) {
                                    throw new RuntimeException(ex);
                                }
                                if (!conn.isClosed()) {
                                    throw new RuntimeException(e);
                                }
                            }
                        } finally {
                            leave(address);
                        }


                    }).start();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }).start();
    }


    @PreDestroy
    public void destroy() {
        try {
            var disableDelayMillis = 3000;
            var maxDisableAttempts = 30;
            System.out.println("Graceful Shutdown - Started");
            this.isShuttingDown = true;
            System.out.println("Graceful Shutdown - Bus Disabled");
            System.out.println("Graceful Shutdown - Sleep...");
            Thread.sleep(disableDelayMillis);
            var retry = 0;
            while (!correlations.isEmpty() && retry < maxDisableAttempts) {
                System.out.printf("Graceful Shutdown - Remaining correlations: %d%n", correlations.size());
                System.out.println("Graceful Shutdown - Sleep...");
                Sleep.apply(disableDelayMillis);
                retry++;
            }
            if (correlations.isEmpty()) {
                System.out.println("Graceful Shutdown - No more correlations, bye!");
            } else {
                System.out.println("Graceful Shutdown - Pending correlation after " + disableDelayMillis * maxDisableAttempts + " sec of retry... so... bye!");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleMessage(EventoMessage m) {
        if (m.getBody() instanceof ClusterNodeIsBoredMessage b) {
            var lockId = "CLUSTER_MANAGE:" + b.getBundleId();
            eventStore.acquire(lockId);
            try {
                var bundle = bundleService.findById(b.getBundleId());
                if (bundle.getBucketType() != BucketType.Ephemeral &&
                        bundle.getMinInstances() <
                                getCurrentAvailableView()
                                        .stream()
                                        .filter(n -> n.bundleId().equals(b.getBundleId())).count())
                    try {
                        sendKill(b.getNodeId());
                    } catch (Exception e) {
                        logger.error("Error trying to kill node %s".formatted(b.getNodeId()), e);
                    }
            } finally {
                eventStore.release(lockId);
            }
        } else if (m.getBody() instanceof ClusterNodeIsSufferingMessage b) {
            var lockId = "CLUSTER_MANAGE:" + b.getBundleId();
            eventStore.acquire(lockId);
            try {
                var bundle = bundleService.findById(b.getBundleId());
                if (bundle.getMaxInstances() > getCurrentAvailableView().stream().filter(n -> n.bundleId().equals(b.getBundleId())).count())
                    try {
                        bundleDeployService.spawn(b.getBundleId());
                    } catch (Exception e) {
                        logger.error("Error trying to spawn bundle %s".formatted(b.getBundleId()), e);
                    }
            } finally {
                eventStore.release(lockId);
            }
        }
        logger.debug("Message received: {}", m);
    }


    private void handleRequest(EventoRequest message, Consumer<EventoResponse> sendResponse) {
        try {
            if (this.isShuttingDown) {
                throw new IllegalStateException("Server is shutting down");
            }
            var request = message.getBody();
            if (request instanceof DomainCommandMessage c) {

                var dest = peekMessageHandlerAddress(c.getCommandName());

                var start = PerformanceStoreService.now();
                var lockId = AGGREGATE_LOCK_PREFIX + c.getAggregateId();
                eventStore.acquire(lockId);
                try {
                    var invocation = new DecoratedDomainCommandMessage();
                    invocation.setCommandMessage(c);
                    var story = eventStore.fetchAggregateStory(c.getAggregateId());
                    invocation.setSerializedAggregateState(story.state());
                    invocation.setEventStream(story.events());
                    performanceStoreService.sendServiceTimeMetric(
                            SERVER,
                            GATEWAY_COMPONENT,
                            c,
                            start
                    );
                    var invocationStart = PerformanceStoreService.now();
                    message.setBody(invocation);
                    forward(message, dest, resp -> {
                        try {
                            performanceStoreService.sendServiceTimeMetric(
                                    message.getSourceBundleId(),
                                    getComponent(c.getCommandName()),
                                    c,
                                    invocationStart
                            );
                            if (resp.getBody() instanceof DomainCommandResponseMessage cr) {
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
                                resp.setBody(cr.getDomainEventMessage().getSerializedPayload().getSerializedObject());
                            }
                            eventStore.release(lockId);
                            sendResponse.accept(resp);
                        } catch (Exception e) {
                            eventStore.release(lockId);
                            resp.setBody(new ExceptionWrapper(e));
                            sendResponse.accept(resp);
                        }

                    });
                } catch (Exception e) {
                    eventStore.release(lockId);
                    throw e;
                }
            } else if (request instanceof ServiceCommandMessage c) {

                var dest = peekMessageHandlerAddress(c.getCommandName());
                var start = PerformanceStoreService.now();
                var lockId = c.getLockId() == null ? null : SERVICE_LOCK_PREFIX + c.getLockId();
                eventStore.acquire(lockId);
                try {
                    forward(message, dest, resp -> {
                        try {
                            performanceStoreService.sendServiceTimeMetric(
                                    dest.bundleId(),
                                    getComponent(c.getCommandName()),
                                    c,
                                    start
                            );
                            if (resp.getBody() instanceof EventMessage<?> event) {
                                if(event.getSerializedPayload().getObjectClass() != null) {
                                    var esStoreStart = PerformanceStoreService.now();
                                    eventStore.publishEvent((EventMessage<?>) resp.getBody());
                                    performanceStoreService.sendServiceTimeMetric(
                                            EVENT_STORE,
                                            EVENT_STORE_COMPONENT,
                                            event,
                                            esStoreStart
                                    );
                                }
                                resp.setBody(event.getSerializedPayload().getSerializedObject());
                            }
                            eventStore.release(lockId);
                            sendResponse.accept(resp);
                        } catch (Exception e) {
                            eventStore.release(lockId);
                            resp.setBody(new ExceptionWrapper(e));
                            sendResponse.accept(resp);
                        }
                    });
                } catch (Exception e) {
                    eventStore.release(lockId);
                    throw e;
                }
            } else if (request instanceof QueryMessage<?> q) {
                var dest = peekMessageHandlerAddress(q.getQueryName());
                var invocationStart = PerformanceStoreService.now();
                forward(message, dest,
                        resp -> {
                            performanceStoreService.sendServiceTimeMetric(
                                    dest.bundleId(),
                                    getComponent(q.getQueryName()),
                                    q,
                                    invocationStart
                            );
                            sendResponse.accept(resp);
                        }
                );

            } else if (request instanceof EventFetchRequest f) {
                var events = f.getComponentName() == null ? eventStore.fetchEvents(
                        f.getContext(),
                        f.getLastSequenceNumber(),
                        f.getLimit()) : eventStore.fetchEvents(
                        f.getContext(),
                        f.getLastSequenceNumber(),
                        f.getLimit(), handlerService.findAllHandledPayloadsNameByComponentName(f.getComponentName()));
                var resp = new EventoResponse();
                resp.setCorrelationId(message.getCorrelationId());
                resp.setBody(new EventFetchResponse(new ArrayList<>(events.stream().map(EventStoreEntry::toPublishedEvent).collect(Collectors.toList()))));
                sendResponse.accept(resp);

            } else if (request instanceof EventLastSequenceNumberRequest) {
                var resp = new EventoResponse();
                resp.setCorrelationId(message.getCorrelationId());
                resp.setBody(new EventLastSequenceNumberResponse(eventStore.getLastEventSequenceNumber()));
                sendResponse.accept(resp);
            } else {
                throw new IllegalArgumentException("Missing Handler " + ((ServerHandleInvocationMessage) request).getPayload());
            }
        } catch (Exception e) {
            logger.error("Error handling message in server", e);
            sendResponse.accept(tw(message.getCorrelationId(), e));
        }
    }

    private NodeAddress peekMessageHandlerAddress(String messageType) {
        var addresses = getEnabledAddressesFormMessage(messageType);
        if (addresses == null || addresses.isEmpty()) {
            var handler = handlerService.findByPayloadName(messageType);
            if (handler != null && handler.getComponent().getBundle().isAutorun()) {
                waitUntilAvailable(handler.getComponent().getBundle());
            }
            addresses = getEnabledAddressesFormMessage(messageType);
        }
        if (addresses.isEmpty()) {
            throw new RuntimeException("No Bundle available to handle " + messageType);
        }
        return addresses.stream().skip(new Random().nextInt(addresses.size()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No Bundle available to handle " + messageType));
    }

    private EventoResponse tw(String ci, Exception e) {
        var resp = new EventoResponse();
        resp.setCorrelationId(ci);
        resp.setBody(new ExceptionWrapper(e));
        return resp;
    }

    private String getComponent(String commandName) {
        return registrations.values().stream().flatMap(r -> r.getHandlers().stream())
                .filter(h -> commandName.equals(h.getHandledPayload()))
                .filter(h -> h.getComponentType() == ComponentType.Aggregate
                        || h.getComponentType() == ComponentType.Service
                        || h.getComponentType() == ComponentType.Projection
                        || h.getComponentType() == ComponentType.Observer)
                .map(RegisteredHandler::getComponentName)
                .findFirst().orElseThrow();
    }

    private Set<NodeAddress> getEnabledAddressesFormMessage(String payloadName) {
        return handlers.getOrDefault(payloadName, new HashSet<>())
                .stream().filter(availableView::contains)
                .collect(Collectors.toSet());
    }

    private final List<Consumer<Set<NodeAddress>>> availableViewListeners = new ArrayList<>();

    public void addAvailableViewListener(Consumer<Set<NodeAddress>> listener) {
        synchronized (availableViewListeners) {
            availableViewListeners.add(listener);
        }
    }

    public void removeAvailableViewListener(Consumer<Set<NodeAddress>> listener) {
        synchronized (availableViewListeners) {
            availableViewListeners.remove(listener);
        }
    }

    private void enable(NodeAddress address) {
        this.availableView.add(address);
        synchronized (availableViewListeners) {
            availableViewListeners.stream().filter(Objects::nonNull).toList()
                    .forEach(l -> l.accept(availableView));
        }
        synchronized (semaphoreMap) {
            var s = semaphoreMap.get(address.bundleId());
            if (s != null)
                s.release();
        }
        logger.info("ENABLED: {} (v.{}) {}", address.bundleId(), address.bundleVersion(), address.bundleId());
    }

    private void disable(NodeAddress address) {
        this.availableView.remove(address);
        synchronized (availableViewListeners) {
            availableViewListeners.stream().filter(Objects::nonNull).toList().forEach(l -> l.accept(availableView));
        }
        logger.info("DISABLED: {} (v.{}) {}", address.bundleId(), address.bundleVersion(), address.bundleId());
    }


    private final List<Consumer<BundleRegistration>> joinListeners = new ArrayList<>();
    private final List<Consumer<Set<NodeAddress>>> viewListeners = new ArrayList<>();

    public void addViewListener(Consumer<Set<NodeAddress>> listener) {
        synchronized (viewListeners) {
            viewListeners.add(listener);
        }
    }

    public void removeViewListener(Consumer<Set<NodeAddress>> listener) {
        synchronized (viewListeners) {
            viewListeners.remove(listener);
        }
    }

    private NodeAddress join(BundleRegistration registration, ObjectOutputStream conn) {
        var a = new NodeAddress(registration.getBundleId(),
                registration.getBundleVersion(),
                registration.getInstanceId());
        synchronized (handlers) {
            view.put(a, conn);
            registrations.put(a, registration);
            for (RegisteredHandler handler : registration.getHandlers()) {
                if (!handlers.containsKey(handler.getHandledPayload())) {
                    handlers.put(handler.getHandledPayload(), new HashSet<>());
                }
                var h = handlers.get(handler.getHandledPayload());
                h.add(a);
                handlers.put(handler.getHandledPayload(), h);
            }
        }
        synchronized (joinListeners) {
            joinListeners.forEach(l -> l.accept(registration));
        }
        synchronized (viewListeners) {
            viewListeners.forEach(l -> l.accept(view.keySet()));
        }
        logger.info("JOIN: {} (v.{}) {}", registration.getBundleId(), registration.getBundleVersion(), registration.getBundleId());
        return a;
    }

    public void addJoinListener(Consumer<BundleRegistration> listener) {
        synchronized (joinListeners) {
            joinListeners.add(listener);
        }
    }

    private final List<Consumer<NodeAddress>> leaveListeners = new ArrayList<>();


    private void leave(NodeAddress address) {
        if (address == null) return;
        synchronized (handlers) {
            availableView.remove(address);
            view.remove(address);
            for (Set<NodeAddress> value : handlers.values()) {
                value.remove(address);
            }
        }
        synchronized (leaveListeners) {
            leaveListeners.stream().filter(Objects::nonNull).toList().forEach(l -> l.accept(address));
        }
        synchronized (viewListeners) {
            viewListeners.stream().filter(Objects::nonNull).toList().forEach(l -> l.accept(view.keySet()));
        }
        logger.info("LEAVE: {} (v.{}) {}", address.bundleId(), address.bundleVersion(), address.bundleId());
    }

    public void addLeaveListener(Consumer<NodeAddress> onNodeLeave) {
        synchronized (leaveListeners) {
            leaveListeners.add(onNodeLeave);
        }
    }

    public boolean isBundleAvailable(String bundleId) {
        return availableView.stream().anyMatch(n -> n.bundleId().equals(bundleId));
    }

    public void sendKill(String nodeId) {
        var m = new EventoMessage();
        m.setBody(new ClusterNodeKillMessage());
        m.setSourceBundleId("evento-server");
        m.setSourceInstanceId("evento-server");
        m.setSourceBundleVersion(0);
        try {
            var out = view.get(
                    view.keySet().stream().filter(k -> k.instanceId().equals(nodeId)).findFirst().orElseThrow()
            );
            synchronized (out) {
                out.writeObject(m);
            }
        } catch (Exception e) {
            logger.error("Send kill failed", e);
        }
    }


    public void forward(EventoRequest eventoRequest, NodeAddress address, Consumer<EventoResponse> response) throws Exception {
        correlations.put(eventoRequest.getCorrelationId(), response);
        try {
            var out = view.get(address);
            synchronized (out) {
                out.writeObject(eventoRequest);
            }
        } catch (Exception e) {
            correlations.remove(eventoRequest.getCorrelationId());
            throw e;
        }
    }

    public Set<NodeAddress> getCurrentAvailableView() {
        return availableView;
    }

    public Set<NodeAddress> getCurrentView() {
        return view.keySet();
    }


    public void waitUntilAvailable(Bundle bundle) {

        if (!isBundleAvailable(bundle.getId())) {
            var bundleId = bundle.getId();
            logger.info("Bundle %s not available, spawning a new one".formatted(bundleId));
            var lockId = "BUNDLE:" + bundleId;
            try {
                eventStore.acquire(lockId);
                var semaphore = semaphoreMap.getOrDefault(bundleId, new Semaphore(0));
                semaphoreMap.put(bundleId, semaphore);
                if (isBundleAvailable(bundleId)) return;
                bundleDeployService.spawn(bundle);
                if (!semaphore.tryAcquire(120, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Bundle Cannot Start");
                }
                logger.info("New %s bundle spawned".formatted(bundleId));

            } catch (Exception e) {
                logger.error("Spawning for %s bundle failed".formatted(bundleId), e);
                throw new RuntimeException(e);
            } finally {
                semaphoreMap.remove(bundleId);
                eventStore.release(lockId);
            }
        }
    }
}
