package org.evento.server.bus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evento.common.messaging.consumer.EventFetchRequest;
import org.evento.common.messaging.consumer.EventFetchResponse;
import org.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
import org.evento.common.messaging.consumer.EventLastSequenceNumberResponse;
import org.evento.common.modeling.bundle.types.ComponentType;
import org.evento.common.modeling.exceptions.ThrowableWrapper;
import org.evento.common.modeling.messaging.message.application.*;
import org.evento.common.modeling.messaging.message.internal.EventoMessage;
import org.evento.common.modeling.messaging.message.internal.EventoRequest;
import org.evento.common.modeling.messaging.message.internal.EventoResponse;
import org.evento.common.modeling.messaging.message.internal.ServerHandleInvocationMessage;
import org.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;
import org.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import org.evento.common.modeling.state.SerializedAggregateState;
import org.evento.common.serialization.ObjectMapperUtils;
import org.evento.server.domain.model.BucketType;
import org.evento.server.domain.model.Bundle;
import org.evento.server.es.EventStore;
import org.evento.server.es.eventstore.EventStoreEntry;
import org.evento.server.service.BundleService;
import org.evento.server.service.HandlerService;
import org.evento.server.service.deploy.BundleDeployService;
import org.evento.server.service.performance.PerformanceStoreService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.evento.common.performance.PerformanceService.*;

@Component
public class MessageBus {
    private final int socketPort;
    private final ObjectMapper mapper = ObjectMapperUtils.getPayloadObjectMapper();

    private final HashMap<NodeAddress, Socket> view = new HashMap<>();
    private final HashMap<NodeAddress, BundleRegistration> registrations = new HashMap<>();
    private final Set<NodeAddress> availableView = new HashSet<>();
    private final Map<String, Set<NodeAddress>> handlers = new HashMap<>();

    private final BundleDeployService bundleDeployService;

    private static final String AGGREGATE_LOCK_PREFIX = "AGGREGATE:";
    private static final String SERVICE_LOCK_PREFIX = "SERVICE:";
    private final HandlerService handlerService;

    private final LockRegistry lockRegistry;

    private final EventStore eventStore;


    private final PerformanceStoreService performanceStoreService;

    private final BundleService bundleService;

    private final Map<String, Consumer<EventoResponse>> correlations = new HashMap<>();

    public MessageBus(
            @Value("${socket.port}") int socketPort, BundleDeployService bundleDeployService, HandlerService handlerService, LockRegistry lockRegistry, EventStore eventStore, PerformanceStoreService performanceStoreService, BundleService bundleService) {
        this.socketPort = socketPort;
        this.bundleDeployService = bundleDeployService;
        this.handlerService = handlerService;
        this.lockRegistry = lockRegistry;
        this.eventStore = eventStore;
        this.performanceStoreService = performanceStoreService;
        this.bundleService = bundleService;
    }

    @PostConstruct
    public void init() throws IOException {

        new Thread(() -> {
            for (Bundle bundle : bundleService.findAllBundles()) {
                if (bundle.isAutorun() && bundle.getBucketType() != BucketType.Ephemeral)
                    bundleDeployService.waitUntilAvailable(bundle.getId());
            }
        }).start();

        var server = new ServerSocket(socketPort);
        while (true) {
            var conn = server.accept();
            new Thread(() -> {
                NodeAddress address = null;
                try {
                    try {
                        var in = new DataInputStream(conn.getInputStream());
                        var out = new DataOutputStream(conn.getOutputStream());

                        final var a = join(mapper.readValue(in.readUTF(), BundleRegistration.class), conn);
                        address = a;

                        while (true) {
                            var message = mapper.readValue(in.readUTF(), Serializable.class);

                            new Thread(() -> {
                                try {
                                    if (message instanceof String s) {
                                        if ("DISABLE".equals(s)) {
                                            disable(a);
                                        } else if ("ENABLE".equals(s)) {
                                            enable(a);
                                        }
                                    } else if (message instanceof EventoRequest r) {
                                        handleRequest(r, resp -> {
                                            try {
                                                out.writeUTF(mapper.writeValueAsString(resp));
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
                    } catch (IOException e) {
                        try {
                            if (!conn.isClosed())
                                conn.close();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                        throw new RuntimeException(e);
                    }
                } finally {
                    leave(address);
                }


            }).start();
        }
    }

    private void handleMessage(EventoMessage m) {

    }

    private NodeAddress peek(Set<NodeAddress> addresses) {
        return addresses.stream().skip(new Random().nextInt(addresses.size())).findFirst().orElse(null);
    }

    private void handleRequest(EventoRequest message, Consumer<EventoResponse> sendResponse) {
        try {
            var request = message.getBody();
            if (request instanceof DomainCommandMessage c) {

                var addresses = getEnabledAddressesFormMessage(c.getCommandName());
                if (addresses == null || addresses.isEmpty()) {
                    var handler = handlerService.findByPayloadName(c.getCommandName());
                    if (handler != null) {
                        bundleDeployService.waitUntilAvailable(handler.getComponent().getBundle().getId());
                    }
                    addresses = getEnabledAddressesFormMessage(c.getCommandName());
                }
                var start = PerformanceStoreService.now();
                var lock = lockRegistry.obtain(AGGREGATE_LOCK_PREFIX + c.getAggregateId());
                lock.lock();
                var semaphore = new Semaphore(0);

                var dest = peek(addresses);

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
                    forward(message, dest, resp -> {
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
                            resp.setBody(null);
                        }
                        sendResponse.accept(resp);
                        semaphore.release();
                        if (resp.getBody() instanceof DomainCommandResponseMessage cr) {
                            sendEventToObservers(message, cr.getDomainEventMessage());
                        }
                    });
                } catch (Exception e) {
                    semaphore.release();
                    throw e;
                } finally {
                    semaphore.acquire();
                    lock.unlock();
                }

            } else if (request instanceof ServiceCommandMessage c) {
                var addresses = getEnabledAddressesFormMessage(c.getCommandName());
                if (addresses == null || addresses.isEmpty()) {
                    var handler = handlerService.findByPayloadName(c.getCommandName());
                    if (handler != null) {
                        bundleDeployService.waitUntilAvailable(handler.getComponent().getBundle().getId());
                    }
                    addresses = getEnabledAddressesFormMessage(c.getCommandName());
                }
                var start = PerformanceStoreService.now();
                var lock = lockRegistry.obtain(SERVICE_LOCK_PREFIX + c.getLockId());
                lock.lock();
                var semaphore = new Semaphore(0);
                var dest = peek(addresses);

                try {
                    forward(message, dest, resp -> {
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
                                    ((EventMessage<?>) resp.getBody()),
                                    esStoreStart
                            );
                            resp.setBody(null);

                        }
                        sendResponse.accept(resp);
                        semaphore.release();
                        if (resp.getBody() instanceof EventMessage<?> event) {
                            sendEventToObservers(message, event);
                        }
                    });
                } catch (Exception e) {
                    semaphore.release();
                    throw e;
                } finally {
                    semaphore.acquire();
                    lock.unlock();
                }

            } else if (request instanceof QueryMessage<?> q) {
                var addresses = getEnabledAddressesFormMessage(q.getQueryName());
                if (addresses == null || addresses.isEmpty()) {
                    var handler = handlerService.findByPayloadName(q.getQueryName());
                    if (handler != null) {
                        bundleDeployService.waitUntilAvailable(handler.getComponent().getBundle().getId());
                    }
                    addresses = getEnabledAddressesFormMessage(q.getQueryName());
                }
                var dest = peek(addresses);
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

    private EventoResponse tw(String ci, Exception e) {
        var resp = new EventoResponse();
        resp.setCorrelationId(ci);
        resp.setBody(new ThrowableWrapper(e.getClass(), e.getMessage(), e.getStackTrace()));
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
    }

    private void disable(NodeAddress address) {
        this.availableView.remove(address);
        availableViewListeners.forEach(l -> l.accept(availableView));
    }


    private final List<Consumer<BundleRegistration>> joinListeners = new ArrayList<>();
    private final List<Consumer<Set<NodeAddress>>> viewListeners = new ArrayList<>();
    public void addViewListener(Consumer<Set<NodeAddress>> listener) {
        viewListeners.add(listener);
    }
    public void removeViewListener(Consumer<Set<NodeAddress>> listener) {
        viewListeners.remove(listener);
    }

    private NodeAddress join(BundleRegistration registration, Socket conn) {
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
            }
        }
        joinListeners.forEach(l -> l.accept(registration));
        viewListeners.forEach(l -> l.accept(view.keySet()));
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
            new DataOutputStream(view.get(
                    view.keySet().stream().filter(k -> k.getInstanceId().equals(nodeId)).findFirst().orElseThrow()
            ).getOutputStream()).writeUTF(mapper.writeValueAsString(m));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void forward(EventoRequest eventoRequest, NodeAddress address, Consumer<EventoResponse> response) throws Exception {
        correlations.put(eventoRequest.getCorrelationId(), response);
        try {
            new DataOutputStream(view.get(address).getOutputStream()).writeUTF(mapper.writeValueAsString(eventoRequest));
        } catch (Exception e) {
            correlations.remove(eventoRequest.getCorrelationId());
            throw e;
        }
    }

    private void sendEventToObservers(EventoRequest request, EventMessage<?> eventMessage) {
        if(getComponent(eventMessage.getEventName())!=null){
            var addresses = getEnabledAddressesFormMessage(eventMessage.getEventName());
            if (addresses == null || addresses.isEmpty()) {
                var handler = handlerService.findByPayloadName(eventMessage.getEventName());
                if (handler != null) {
                    bundleDeployService.waitUntilAvailable(handler.getComponent().getBundle().getId());
                }
                addresses = getEnabledAddressesFormMessage(eventMessage.getEventName());
            }
            for (NodeAddress address : addresses) {
                var m = new EventoMessage();
                m.setBody(eventMessage);
                m.setSourceBundleId(request.getSourceBundleId());
                m.setSourceInstanceId(request.getSourceInstanceId());
                m.setSourceBundleVersion(request.getSourceBundleVersion());
                try {
                    new DataOutputStream(view.get(address).getOutputStream()).writeUTF(mapper.writeValueAsString(m));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Set<NodeAddress> getCurrentAvailableView() {
        return availableView;
    }
    public  Set<NodeAddress>  getCurrentView() {
        return view.keySet();
    }
}
