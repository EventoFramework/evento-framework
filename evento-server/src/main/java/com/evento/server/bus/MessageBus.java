package com.evento.server.bus;

import com.evento.common.modeling.messaging.message.internal.discovery.BundleConsumerRegistrationMessage;
import com.evento.common.serialization.ObjectMapperUtils;
import com.evento.server.service.discovery.ConsumerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import com.evento.common.messaging.consumer.EventFetchRequest;
import com.evento.common.messaging.consumer.EventFetchResponse;
import com.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
import com.evento.common.messaging.consumer.EventLastSequenceNumberResponse;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.application.*;
import com.evento.common.modeling.messaging.message.internal.*;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.common.performance.PerformanceInvocationsMessage;
import com.evento.common.performance.PerformanceServiceTimeMessage;
import com.evento.common.utils.Sleep;
import com.evento.server.domain.model.core.BucketType;
import com.evento.server.domain.model.core.Bundle;
import com.evento.server.es.EventStore;
import com.evento.server.es.eventstore.EventStoreEntry;
import com.evento.server.service.BundleService;
import com.evento.server.service.HandlerService;
import com.evento.server.service.deploy.BundleDeployService;
import com.evento.server.service.performance.PerformanceStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.evento.common.performance.PerformanceService.*;

@Component
public class MessageBus {


    private final Logger logger = LoggerFactory.getLogger(MessageBus.class);
    private final int socketPort;

    private final Map<NodeAddress, ObjectOutputStream> view = new ConcurrentHashMap<>();
    private final Map<NodeAddress, BundleRegistration> registrations = new ConcurrentHashMap<>();
    private final Set<NodeAddress> availableView = new HashSet<>();
    private final Map<String, Set<NodeAddress>> handlers = new ConcurrentHashMap<>();

    private final BundleDeployService bundleDeployService;

    private static final String RESOURCE_LOCK_PREFIX = "RESOURCE:";
    private static final String BUNDLE_LOCK_PREFIX = "BUNDLE:";
    private static final String CLUSTER_LOCK_PREFIX = "CLUSTER:";
    private final HandlerService handlerService;
    private final String instanceId;

    private final EventStore eventStore;


    private final ConcurrentHashMap<String, Semaphore> semaphoreMap = new ConcurrentHashMap<>();


    private final PerformanceStoreService performanceStoreService;

    private final BundleService bundleService;

    private final Map<String, Correlation> correlations = new ConcurrentHashMap<>();
    private final ConsumerService consumerService;
    private boolean isShuttingDown = false;

    private final Executor threadPerMessageExecutor = Executors.newCachedThreadPool();

    public MessageBus(
            @Value("${socket.port}") int socketPort,
            @Value("${evento.server.instance.id}") String instanceId,
            BundleDeployService bundleDeployService,
            HandlerService handlerService,
            EventStore eventStore,
            PerformanceStoreService performanceStoreService,
            BundleService bundleService, ConsumerService consumerService) {
        this.socketPort = socketPort;
        this.bundleDeployService = bundleDeployService;
        this.handlerService = handlerService;
        this.eventStore = eventStore;
        this.performanceStoreService = performanceStoreService;
        this.bundleService = bundleService;
        this.instanceId = instanceId;
        this.consumerService = consumerService;
    }

    @PostConstruct
    public void init() {

        var t = new Thread(() -> {
            for (Bundle bundle : bundleService.findAllBundles()) {
                if (bundle.isDeployable() && bundle.isAutorun() && bundle.getBucketType() != BucketType.Ephemeral)
                    waitUntilAvailable(bundle);
            }
        });
        t.setName("Bundle autostart Thread");
        t.start();

        t = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(socketPort)) {
                while (true) {
                    var conn = server.accept();
                    logger.info("New connection: " + conn.getInetAddress() + ":" + conn.getPort());
                    var it = new Thread(() -> {
                        NodeAddress address = null;
                        try {
                            try {
                                var in = new ObjectInputStream(conn.getInputStream());
                                var out = new ObjectOutputStream(conn.getOutputStream());
                                final var a = join((BundleRegistration) in.readObject(), out);
                                synchronized (out) {
                                    out.writeObject(true);
                                    out.flush();
                                }
                                address = a;

                                while (true) {
                                    var message = in.readObject();
                                    threadPerMessageExecutor.execute(() -> {
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
                                                            out.flush();
                                                        }
                                                    } catch (IOException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                });
                                            } else if (message instanceof EventoResponse r) {
                                                var c = correlations.get(r.getCorrelationId());
                                                correlations.remove(r.getCorrelationId());
                                                c.response().accept(r);
                                            } else if (message instanceof EventoMessage m) {
                                                handleMessage(m);
                                            }
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                for (Map.Entry<String, Correlation> ek : correlations.entrySet()) {
                                    var resp = new EventoResponse();
                                    resp.setCorrelationId(ek.getKey());
                                    resp.setBody(new ExceptionWrapper(e));
                                    try {
                                        ek.getValue().response().accept(resp);
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


                    });
                    it.setName("Client connection " + conn.getInetAddress() + ":" + conn.getPort());
                    it.start();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        });
        t.setName("MessageBus ConnectionHandler Thread");
        t.start();
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
                correlations.forEach((k, v) -> {
                    System.out.printf("Graceful Shutdown - Pending correlation: %s%n", k);
                    System.out.println("Graceful Shutdown - Body:");
                    try {
                        System.out.println(ObjectMapperUtils.getPayloadObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(v.request()));
                    } catch (JsonProcessingException ignored) {
                    }

                });
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
        logger.debug("Message received: {}", m);
        if (m.getBody() instanceof ClusterNodeIsBoredMessage b) {
            var lockId = CLUSTER_LOCK_PREFIX + b.getBundleId();
            eventStore.acquire(lockId);
            try {
                var bundle = bundleService.findById(b.getBundleId());
                if (bundle.getBucketType() != BucketType.Ephemeral &&
                        bundle.getMinInstances() <
                                getCurrentAvailableView()
                                        .stream()
                                        .filter(n -> n.bundleId().equals(b.getBundleId())).count())
                    try {
                        sendKill(b.getInstanceId());
                    } catch (Exception e) {
                        logger.error("Error trying to kill node %s".formatted(b.getInstanceId()), e);
                    }
            } finally {
                eventStore.release(lockId);
            }
        } else if (m.getBody() instanceof ClusterNodeIsSufferingMessage b) {
            var lockId = CLUSTER_LOCK_PREFIX + b.getBundleId();
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
        } else if (m.getBody() instanceof PerformanceInvocationsMessage im) {
            performanceStoreService.saveInvocationsPerformance(
                    im.getBundle(),
                    im.getInstanceId(),
                    im.getComponent(),
                    im.getAction(),
                    im.getInvocations()
            );
        } else if (m.getBody() instanceof PerformanceServiceTimeMessage im) {
            performanceStoreService.saveServiceTimePerformance(
                    im.getBundle(),
                    im.getInstanceId(),
                    im.getComponent(),
                    im.getAction(),
                    im.getStart(),
                    im.getEnd()
            );
        } else if (m.getBody() instanceof BundleConsumerRegistrationMessage cr) {
            consumerService.registerConsumers(m.getSourceBundleId(), m.getSourceInstanceId(), m.getSourceBundleVersion(), cr);
        }
    }


    private void handleRequest(EventoRequest message, Consumer<EventoResponse> sendResponse) {
        try {
            if (this.isShuttingDown) {
                throw new IllegalStateException("Server is shutting down");
            }
            var request = message.getBody();
            switch (request) {
                case DomainCommandMessage c -> {
                    logger.debug("Handle DomainCommandMessage: {}", message);
                    var dest = peekMessageHandlerAddress(c.getCommandName());
                    var start = PerformanceStoreService.now();
                    var lockId = c.getLockId() == null ? null : RESOURCE_LOCK_PREFIX + c.getLockId();
                    eventStore.acquire(lockId);
                    logger.trace("Handle DomainCommandMessage({}) - lock acquired: {}", message.getCorrelationId(), lockId);
                    Instant lockAcquired = PerformanceStoreService.now();
                    AtomicBoolean acquired = new AtomicBoolean(lockId != null);
                    try {
                        var invocation = new DecoratedDomainCommandMessage();
                        invocation.setCommandMessage(c);
                        var story = eventStore.fetchAggregateStory(c.getAggregateId(),
                                c.isInvalidateAggregateCaches(),
                                c.isInvalidateAggregateSnapshot());
                        logger.trace("Handle DomainCommandMessage({}) - Story Fetched: {}", message.getCorrelationId(), story.events().size());
                        invocation.setSerializedAggregateState(story.state());
                        invocation.setEventStream(story.events());
                        var retrieveDone = performanceStoreService.sendServiceTimeMetric(
                                SERVER,
                                instanceId,
                                GATEWAY_COMPONENT,
                                c,
                                start,
                                c.isForceTelemetry()
                        );
                        var invocationStart = PerformanceStoreService.now();
                        message.setBody(invocation);
                        logger.trace("Handle DomainCommandMessage({}) - Forward Invocation: {}", message.getCorrelationId(), invocation);
                        forward(message, dest, resp -> {
                            try {
                                logger.trace("Handle DomainCommandMessage({}) - Invocation Response: {}", message.getCorrelationId(), resp);
                                var computationDone = performanceStoreService.sendServiceTimeMetric(
                                        dest.bundleId(),
                                        dest.instanceId(),
                                        getComponent(c.getCommandName()),
                                        c,
                                        invocationStart,
                                        c.isForceTelemetry()
                                );
                                if (resp.getBody() instanceof DomainCommandResponseMessage cr) {
                                    cr.getDomainEventMessage().setForceTelemetry(c.isForceTelemetry());
                                    var eventSequenceNumber = eventStore.publishEvent(cr.getDomainEventMessage(),
                                            c.getAggregateId());
                                    logger.trace("Handle DomainCommandMessage({}) - Event Published: {} - {}", message.getCorrelationId(), c.getAggregateId(), cr.getDomainEventMessage());
                                    if (cr.getSerializedAggregateState() != null) {
                                        eventStore.saveSnapshot(
                                                c.getAggregateId(),
                                                eventSequenceNumber,
                                                cr.getSerializedAggregateState()
                                        );
                                        logger.trace("Handle DomainCommandMessage({}) - Snapshot Saved: {} - {}", message.getCorrelationId(), c.getAggregateId(), cr.getSerializedAggregateState());
                                    }
                                    if (cr.isAggregateDeleted()) {
                                        eventStore.deleteAggregate(c.getAggregateId());
                                        logger.trace("Handle DomainCommandMessage({}) - Aggregate Deleted: {}", message.getCorrelationId(), c.getAggregateId());
                                    }
                                    var published = performanceStoreService.sendServiceTimeMetric(
                                            EVENT_STORE,
                                            instanceId,
                                            EVENT_STORE_COMPONENT,
                                            cr.getDomainEventMessage(),
                                            computationDone,
                                            c.isForceTelemetry()
                                    );
                                    resp.setBody(cr.getDomainEventMessage().getSerializedPayload().getSerializedObject());
                                    performanceStoreService.sendAggregateServiceTimeMetric(
                                            dest.bundleId(),
                                            dest.instanceId(),
                                            message.getSourceBundleId(),
                                            message.getSourceInstanceId(),
                                            eventSequenceNumber,
                                            getComponent(c.getCommandName()),
                                            c.getCommandName(),
                                            start,
                                            lockAcquired,
                                            retrieveDone,
                                            computationDone,
                                            published,
                                            c.getAggregateId(),
                                            c.isForceTelemetry()
                                    );
                                }
                                eventStore.release(lockId);
                                acquired.set(false);
                                sendResponse.accept(resp);
                            } catch (Exception e) {
                                try {
                                    if (acquired.get()) {
                                        eventStore.release(lockId);
                                    }
                                } catch (Exception ie) {
                                    logger.error("Error unlocking after exception", ie);
                                }
                                resp.setBody(new ExceptionWrapper(e));
                                sendResponse.accept(resp);
                            }
                            logger.trace("Handle DomainCommandMessage({}) - Response Sent: {}", message.getCorrelationId(), resp);

                        });
                    } catch (Exception e) {
                        eventStore.release(lockId);
                        throw e;
                    }
                }
                case ServiceCommandMessage c -> {
                    logger.debug("Handle ServiceCommandMessage: {}", message);
                    var dest = peekMessageHandlerAddress(c.getCommandName());
                    var start = PerformanceStoreService.now();
                    var lockId = c.getLockId() == null ? null : RESOURCE_LOCK_PREFIX + c.getLockId();
                    eventStore.acquire(lockId);
                    logger.trace("Handle ServiceCommandMessage({}) - lock acquired: {}", message.getCorrelationId(), lockId);
                    AtomicBoolean acquired = new AtomicBoolean(lockId != null);
                    try {
                        logger.trace("Handle ServiceCommandMessage({}) - Forward Invocation: {}", message.getCorrelationId(), message);
                        forward(message, dest, resp -> {
                            try {
                                logger.trace("Handle ServiceCommandMessage({}) - Invocation Response: {}", message.getCorrelationId(), resp);
                                performanceStoreService.sendServiceTimeMetric(
                                        dest.bundleId(),
                                        dest.instanceId(),
                                        getComponent(c.getCommandName()),
                                        c,
                                        start,
                                        c.isForceTelemetry()
                                );
                                if (resp.getBody() instanceof EventMessage<?> event) {
                                    if (event.getSerializedPayload().getObjectClass() != null) {
                                        event.setForceTelemetry(c.isForceTelemetry());
                                        var esStoreStart = PerformanceStoreService.now();
                                        eventStore.publishEvent(event,
                                                c.getAggregateId());
                                        logger.trace("Handle ServiceCommandMessage({}) - Event Published: {} - {}", message.getCorrelationId(), c.getAggregateId(), event);
                                        performanceStoreService.sendServiceTimeMetric(
                                                EVENT_STORE,
                                                instanceId,
                                                EVENT_STORE_COMPONENT,
                                                event,
                                                esStoreStart,
                                                c.isForceTelemetry()
                                        );
                                    }
                                    resp.setBody(event.getSerializedPayload().getSerializedObject());
                                }
                                eventStore.release(lockId);
                                acquired.set(false);
                                sendResponse.accept(resp);
                            } catch (Exception e) {
                                try {
                                    if (acquired.get()) {
                                        eventStore.release(lockId);
                                    }
                                } catch (Exception ie) {
                                    logger.error("Error unlocking after exception", ie);
                                }
                                resp.setBody(new ExceptionWrapper(e));
                                sendResponse.accept(resp);
                            }
                            logger.trace("Handle ServiceCommandMessage({}) - Response Sent: {}", message.getCorrelationId(), resp);
                        });
                    } catch (Exception e) {
                        eventStore.release(lockId);
                        throw e;
                    }
                }
                case QueryMessage<?> q -> {
                    logger.debug("Handle QueryMessage: {}", message);
                    var dest = peekMessageHandlerAddress(q.getQueryName());
                    var invocationStart = PerformanceStoreService.now();
                    logger.trace("Handle QueryMessage({}) - Forward Invocation: {}", message.getCorrelationId(), message);
                    forward(message, dest,
                            resp -> {
                                logger.trace("Handle QueryMessage({}) - Invocation Response: {}", message.getCorrelationId(), resp);
                                performanceStoreService.sendServiceTimeMetric(
                                        dest.bundleId(),
                                        dest.instanceId(),
                                        getComponent(q.getQueryName()),
                                        q,
                                        invocationStart,
                                        q.isForceTelemetry()
                                );
                                sendResponse.accept(resp);
                                logger.trace("Handle QueryMessage({}) - Response Sent: {}", message.getCorrelationId(), resp);
                            }
                    );

                }
                case EventFetchRequest f -> {
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

                }
                case EventLastSequenceNumberRequest ignored -> {
                    var resp = new EventoResponse();
                    resp.setCorrelationId(message.getCorrelationId());
                    resp.setBody(new EventLastSequenceNumberResponse(eventStore.getLastEventSequenceNumber()));
                    sendResponse.accept(resp);
                }
                case null, default ->
                        throw new IllegalArgumentException("Missing Handler for " + (request != null ? request.getClass() : null));
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
            if (handler != null && handler.getComponent().getBundle().isAutorun()
                    && handler.getComponent().getBundle().isDeployable()) {
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

    public void sendKill(String instanceId) {
        var m = new EventoMessage();
        m.setBody(new ClusterNodeKillMessage());
        m.setSourceBundleId("evento-server");
        m.setSourceInstanceId(instanceId);
        m.setSourceBundleVersion(0);
        try {
            var out = view.get(
                    view.keySet().stream().filter(k -> k.instanceId().equals(instanceId)).findFirst().orElseThrow()
            );
            synchronized (out) {
                out.writeObject(m);
                out.flush();
            }
        } catch (Exception e) {
            logger.error("Send kill failed", e);
        }
    }


    public void forward(EventoRequest eventoRequest, NodeAddress address, Consumer<EventoResponse> response) throws Exception {
        correlations.put(eventoRequest.getCorrelationId(), new Correlation(eventoRequest, response));
        try {
            var out = view.get(address);
            synchronized (out) {
                out.writeObject(eventoRequest);
                out.flush();
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
            var lockId = BUNDLE_LOCK_PREFIX + bundleId;
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
