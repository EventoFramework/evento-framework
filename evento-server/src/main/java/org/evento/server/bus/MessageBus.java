package org.evento.server.bus;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.evento.common.serialization.ObjectMapperUtils;
import org.evento.server.domain.model.BucketType;
import org.evento.server.domain.model.Bundle;
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
    private final ObjectMapper mapper = ObjectMapperUtils.getPayloadObjectMapper();

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

    private final Map<String, Consumer<EventoResponse>> correlations = new HashMap<>();

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
    public void init() throws IOException {

        new Thread(() -> {
            for (Bundle bundle : bundleService.findAllBundles()) {
                if (bundle.isAutorun() && bundle.getBucketType() != BucketType.Ephemeral)
                    waitUntilAvailable(bundle);
            }
        }).start();

        new Thread(() -> {
            try {
                ServerSocket server = new ServerSocket(socketPort);
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
                                                        out.writeObject(resp);
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

    private void handleMessage(EventoMessage m) {

    }


    private void handleRequest(EventoRequest message, Consumer<EventoResponse> sendResponse) {
        try {
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
                    invocation.setSerializedAggregateState(story.getState());
                    invocation.setEventStream(story.getEvents());
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
                            if (resp.getBody() instanceof DomainCommandResponseMessage cr) {
                                sendEventToObservers(message, cr.getDomainEventMessage());
                            }
                        }catch (Exception e){
                            eventStore.release(lockId);
                            resp.setBody(new ExceptionWrapper(e));
                            sendResponse.accept(resp);
                        }

                    });
                }catch (Exception e){
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
                                    dest.getBundleId(),
                                    getComponent(c.getCommandName()),
                                    c,
                                    start
                            );
                            if (resp.getBody() instanceof EventMessage<?> event) {
                                var esStoreStart = PerformanceStoreService.now();
                                eventStore.publishEvent((EventMessage<?>) resp.getBody());
                                performanceStoreService.sendServiceTimeMetric(
                                        EVENT_STORE,
                                        EVENT_STORE_COMPONENT,
                                        event,
                                        esStoreStart
                                );
                                resp.setBody(event.getSerializedPayload().getSerializedObject());
                            }
                            eventStore.release(lockId);
                            sendResponse.accept(resp);
                            if (resp.getBody() instanceof EventMessage<?> event) {
                                sendEventToObservers(message, event);
                            }
                        }catch (Exception e){
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
                                    dest.getBundleId(),
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

            } else if (request instanceof EventLastSequenceNumberRequest r) {
                var resp = new EventoResponse();
                resp.setCorrelationId(message.getCorrelationId());
                resp.setBody(new EventLastSequenceNumberResponse(eventStore.getLastEventSequenceNumber()));
                sendResponse.accept(resp);
            } else {
                throw new IllegalArgumentException("Missing Handler " + ((ServerHandleInvocationMessage) request).getPayload());
            }
        } catch (Exception e) {
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

    private boolean getObservers(String eventName) {
        return registrations.values().stream().flatMap(r -> r.getHandlers().stream())
                .anyMatch(h -> h.getComponentType() == ComponentType.Observer && eventName.equals(h.getHandledPayload()));
    }

    private Set<NodeAddress> getEnabledAddressesFormMessage(String commandName) {
        return handlers.getOrDefault(commandName, new HashSet<>())
                .stream().filter(availableView::contains)
                .collect(Collectors.toSet());
    }


    private final List<Consumer<Set<NodeAddress>>> availableViewListeners = new ArrayList<>();

    public void addAvailableViewListener(Consumer<Set<NodeAddress>> listener) {
        availableViewListeners.add(listener);
    }

    public void removeAvailableViewListener(Consumer<Set<NodeAddress>> listener) {
        availableViewListeners.remove(listener);
    }

    private void enable(NodeAddress address) {
        this.availableView.add(address);
        availableViewListeners.forEach(l -> l.accept(availableView));
        synchronized (semaphoreMap) {
            var s = semaphoreMap.get(address.getBundleId());
            if (s != null)
                s.release();
        }
        logger.info("ENABLED: {} (v.{}) {}", address.getBundleId(), address.getBundleVersion(), address.getBundleId());
    }

    private void disable(NodeAddress address) {
        this.availableView.remove(address);
        availableViewListeners.forEach(l -> l.accept(availableView));
        logger.info("DISABLED: {} (v.{}) {}", address.getBundleId(), address.getBundleVersion(), address.getBundleId());
    }


    private final List<Consumer<BundleRegistration>> joinListeners = new ArrayList<>();
    private final List<Consumer<Set<NodeAddress>>> viewListeners = new ArrayList<>();

    public void addViewListener(Consumer<Set<NodeAddress>> listener) {
        viewListeners.add(listener);
    }

    public void removeViewListener(Consumer<Set<NodeAddress>> listener) {
        viewListeners.remove(listener);
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
        joinListeners.forEach(l -> l.accept(registration));
        viewListeners.forEach(l -> l.accept(view.keySet()));
        logger.info("JOIN: {} (v.{}) {}", registration.getBundleId(), registration.getBundleVersion(), registration.getBundleId());
        return a;
    }

    public void addJoinListener(Consumer<BundleRegistration> listener) {
        joinListeners.add(listener);
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
        leaveListeners.forEach(l -> l.accept(address));
        viewListeners.forEach(l -> l.accept(view.keySet()));
        logger.info("LEAVE: {} (v.{}) {}", address.getBundleId(), address.getBundleVersion(), address.getBundleId());
    }

    public void addLeaveListener(Consumer<NodeAddress> onNodeLeave) {
        leaveListeners.add(onNodeLeave);
    }

    public boolean isBundleAvailable(String bundleId) {
        return availableView.stream().anyMatch(n -> n.getBundleId().equals(bundleId));
    }

    public void sendKill(String nodeId) {
        var m = new EventoMessage();
        m.setBody("KILL");
        try {
            view.get(
                    view.keySet().stream().filter(k -> k.getInstanceId().equals(nodeId)).findFirst().orElseThrow()
            ).writeObject(m);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void forward(EventoRequest eventoRequest, NodeAddress address, Consumer<EventoResponse> response) throws Exception {
        correlations.put(eventoRequest.getCorrelationId(), response);
        try {
            view.get(address).writeObject(eventoRequest);
        } catch (Exception e) {
            correlations.remove(eventoRequest.getCorrelationId());
            throw e;
        }
    }

    private void sendEventToObservers(EventoRequest request, EventMessage<?> eventMessage) {
        if (getObservers(eventMessage.getEventName())) {
            var addresses = getEnabledAddressesFormMessage(eventMessage.getEventName());
            if (addresses == null || addresses.isEmpty()) {
                var handler = handlerService.findByPayloadName(eventMessage.getEventName());
                if (handler != null && handler.getComponent().getBundle().isAutorun()) {
                    waitUntilAvailable(handler.getComponent().getBundle());
                }
                addresses = getEnabledAddressesFormMessage(eventMessage.getEventName());
            }
            addresses.parallelStream().forEach(address -> {
                var m = new EventoMessage();
                m.setBody(eventMessage);
                m.setSourceBundleId(request.getSourceBundleId());
                m.setSourceInstanceId(request.getSourceInstanceId());
                m.setSourceBundleVersion(request.getSourceBundleVersion());
                try {
                    view.get(address).writeObject(m);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
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
