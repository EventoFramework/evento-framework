package com.evento.server.bus;

import com.evento.common.messaging.bus.EventoRequestCorrelationExpiredException;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleConsumerRegistrationMessage;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleRegistered;
import com.evento.common.serialization.ObjectMapperUtils;
import com.evento.common.utils.PgDistributedLock;
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

import javax.sql.DataSource;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.evento.common.performance.PerformanceService.*;

@Component
public class MessageBus {


    private final Logger logger = LoggerFactory.getLogger(MessageBus.class);
    private final int socketPort;

    private final Map<NodeAddress, ObjectOutputStream> view = new ConcurrentHashMap<>();
    private final Map<NodeAddress, BundleRegistration> registrations = new ConcurrentHashMap<>();
    private final Set<NodeAddress> availableView = Collections.newSetFromMap(new ConcurrentHashMap<>());
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
    private final long disableDelayMillis;
    private final long maxDisableAttempts;
    private final long heartbeatInterval;

    private final PgDistributedLock distributedLock;

    private final ScheduledExecutorService heartBeatScheduler =
            Executors.newSingleThreadScheduledExecutor();

    private final long correlationCheckInterval;
    private final long sendRetryMaxAttempts;
    private final long sendRetryDelayMillis;

    private final ScheduledExecutorService pendingCorrelationScheduler =
            Executors.newSingleThreadScheduledExecutor();

    public MessageBus(
            @Value("${socket.port}") int socketPort,
            @Value("${evento.server.instance.id}") String instanceId,
            BundleDeployService bundleDeployService,
            HandlerService handlerService,
            EventStore eventStore,
            PerformanceStoreService performanceStoreService,
            BundleService bundleService, ConsumerService consumerService,
            @Value("${evento.server.disable.delay.millis:3000}") long disableDelayMillis,
            @Value("${evento.server.max.disable.attempts:30}") long maxDisableAttempts,
            @Value("${evento.server.heart.beat.interval:15000}") long heartbeatInterval,
            @Value("${evento.server.correlation.check.interval:1000}") long correlationCheckInterval,
            @Value("${evento.server.send.retry.max.attempts:5}") long sendRetryMaxAttempts,
            @Value("${evento.server.send.retry.delay.millis:3000}") long sendRetryDelayMillis,
            DataSource dataSource) {
        this.socketPort = socketPort;
        this.bundleDeployService = bundleDeployService;
        this.handlerService = handlerService;
        this.eventStore = eventStore;
        this.performanceStoreService = performanceStoreService;
        this.bundleService = bundleService;
        this.instanceId = instanceId;
        this.consumerService = consumerService;
        this.disableDelayMillis = disableDelayMillis;
        this.maxDisableAttempts = maxDisableAttempts;
        this.heartbeatInterval = heartbeatInterval;
        this.distributedLock = new PgDistributedLock(dataSource);
        this.correlationCheckInterval = correlationCheckInterval;
        this.sendRetryMaxAttempts = sendRetryMaxAttempts;
        this.sendRetryDelayMillis = sendRetryDelayMillis;
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
                        AtomicReference<NodeAddress> address = new AtomicReference<>();
                        try {
                            var in = new ObjectInputStream(conn.getInputStream());
                            var out = new ObjectOutputStream(conn.getOutputStream());
                            boolean registered = false;
                            ExceptionWrapper registrationException = null;
                            var registration = (BundleRegistration) in.readObject();
                            try {
                                address.set(join(registration, out));
                                registered = true;
                            } catch (Exception e) {
                                registrationException = new ExceptionWrapper(e);
                            }
                            synchronized (out) {
                                out.writeObject(new BundleRegistered(
                                        registration.getBundleId(),
                                        registration.getBundleVersion(),
                                        registration.getInstanceId(),
                                        instanceId,
                                        registered,
                                        registrationException
                                ));
                                out.flush();
                            }
                            if (!registered) {
                                logger.error("Error during bundle registration: {}", registrationException);
                                throw new IllegalStateException("Error during bundle registration", registrationException.toException());
                            }

                            while (true) {
                                var message = in.readObject();
                                threadPerMessageExecutor.execute(() -> {
                                    try {
                                        if (message instanceof DisableMessage) {
                                            disable(address.get());
                                        } else if (message instanceof EnableMessage) {
                                            enable(address.get());
                                        } else if (message instanceof EventoRequest r) {
                                            handleRequest(address.get(), r);
                                        } else if (message instanceof EventoResponse r) {
                                            var c = correlations.remove(r.getCorrelationId());
                                            c.getResponse().accept(r);
                                        } else if (message instanceof EventoMessage m) {
                                            handleMessage(m);
                                        } else if (message instanceof ClientHeartBeatMessage hb) {
                                            logger.debug("Received heartbeat from bundle {} client {}", hb.getBundleId(), hb.getInstanceId());
                                        }
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            leave(address.get(), e);
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

        heartBeatScheduler.scheduleWithFixedDelay(
                () -> {
                    if (isShuttingDown) {
                        heartBeatScheduler.shutdown();
                        return;
                    }
                    var hb = UUID.randomUUID() + "_" + System.currentTimeMillis();
                    logger.debug("Sending heartbeat {} from {}", hb, instanceId);
                    for (NodeAddress nodeAddress : this.availableView) {
                        var value = view.get(nodeAddress);
                        try {
                            logger.trace("Sending heartbeat {} to {} - {}", hb, nodeAddress.toString(), nodeAddress.instanceId());
                            value.writeObject(new ServerHeartBeatMessage(instanceId, hb));
                            value.flush();
                        } catch (Throwable e) {
                            logger.error("Error during server heart beat", e);
                            try {
                                value.close();
                            } catch (Throwable ex) {
                                logger.error("Error during server heart beat close", ex);
                            }
                            leave(nodeAddress, e);
                        }
                    }
                },
                0,
                heartbeatInterval,
                TimeUnit.MILLISECONDS);

        pendingCorrelationScheduler.scheduleWithFixedDelay(
                () -> {
                    if (isShuttingDown) {
                        heartBeatScheduler.shutdown();
                        return;
                    }
                    cleanPendingCorrelations();
                },
                correlationCheckInterval,
                correlationCheckInterval,
                TimeUnit.MILLISECONDS
        );
    }


    @PreDestroy
    public void destroy() {
        try {
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
                        System.out.println(ObjectMapperUtils.getPayloadObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(v.getRequest()));
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
                System.out.println("Graceful Shutdown - Pending correlation after " + disableDelayMillis * maxDisableAttempts + " millis of retry... so... bye!");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void cleanPendingCorrelations() {
        logger.debug("Checking for pending correlations...");
        var expired = correlations.values().stream()
                .filter(c -> c.getRequest().checkExpired()).toList();
        logger.debug("Found {} expired correlations of {}", expired.size(), correlations.size());
        for (Correlation c : expired) {
            var resp = new EventoResponse();
            resp.setCorrelationId(c.getRequest().getCorrelationId());
            resp.setRequestTimestamp(c.getRequest().getTimestamp());
            resp.setTimeout(c.getRequest().getTimeout());
            resp.setUnit(c.getRequest().getUnit());
            resp.setBody(new ExceptionWrapper(
                    new EventoRequestCorrelationExpiredException(
                            "Request Expired"
                    )
            ));
            try {
                c.getResponse().accept(resp);
            } catch (Exception ex) {
                logger.error("Error during correlation fail management", ex);
            }
            correlations.remove(c.getRequest().getCorrelationId());
        }
    }

    private void handleMessage(EventoMessage m) {
        logger.debug("Message received: {}", m);
        if (m.getBody() instanceof ClusterNodeIsBoredMessage b) {
            var lockId = CLUSTER_LOCK_PREFIX + b.getBundleId();
            distributedLock.lockedArea(lockId, () -> {
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
            });
        } else if (m.getBody() instanceof ClusterNodeIsSufferingMessage b) {
            var lockId = CLUSTER_LOCK_PREFIX + b.getBundleId();
            distributedLock.lockedArea(lockId, () -> {
                var bundle = bundleService.findById(b.getBundleId());
                if (bundle.getMaxInstances() > getCurrentAvailableView().stream().filter(n -> n.bundleId().equals(b.getBundleId())).count())
                    try {
                        bundleDeployService.spawn(b.getBundleId());
                    } catch (Exception e) {
                        logger.error("Error trying to spawn bundle %s".formatted(b.getBundleId()), e);
                    }
            });
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


    private void handleRequest(NodeAddress from, EventoRequest message) {
        try {
            if (this.isShuttingDown) {
                var resp = new EventoResponse();
                resp.setCorrelationId(message.getCorrelationId());
                resp.setRequestTimestamp(message.getTimestamp());
                resp.setTimeout(message.getTimeout());
                resp.setUnit(message.getUnit());
                resp.setBody(new ExceptionWrapper(new IllegalStateException("Server is shutting down")));
                send(from, resp);
                return;
            }
            var request = message.getBody();
            switch (request) {
                case DomainCommandMessage c -> {
                    logger.debug("Handle DomainCommandMessage: {}", message);
                    var dest = peekMessageHandlerAddress(c.getCommandName());
                    var start = PerformanceStoreService.now();
                    var lockId = c.getLockId() == null ? null : RESOURCE_LOCK_PREFIX + c.getLockId();
                    distributedLock.acquire(lockId);
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
                        forward(from, dest, message, resp -> {
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
                                distributedLock.release(lockId);
                                acquired.set(false);
                                send(from, resp);
                            } catch (Exception e) {
                                try {
                                    if (acquired.get()) {
                                        distributedLock.release(lockId);
                                    }
                                } catch (Exception ie) {
                                    logger.error("Error unlocking after exception", ie);
                                }
                                resp.setBody(new ExceptionWrapper(e));
                                send(from, resp);
                            }
                            logger.trace("Handle DomainCommandMessage({}) - Response Sent: {}", message.getCorrelationId(), resp);

                        });
                    } catch (Exception e) {
                        distributedLock.release(lockId);
                        throw e;
                    }
                }
                case ServiceCommandMessage c -> {
                    logger.debug("Handle ServiceCommandMessage: {}", message);
                    var dest = peekMessageHandlerAddress(c.getCommandName());
                    var start = PerformanceStoreService.now();
                    var lockId = c.getLockId() == null ? null : RESOURCE_LOCK_PREFIX + c.getLockId();
                    distributedLock.acquire(lockId);
                    logger.trace("Handle ServiceCommandMessage({}) - lock acquired: {}", message.getCorrelationId(), lockId);
                    AtomicBoolean acquired = new AtomicBoolean(lockId != null);
                    try {
                        logger.trace("Handle ServiceCommandMessage({}) - Forward Invocation: {}", message.getCorrelationId(), message);
                        forward(from, dest, message, resp -> {
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
                                distributedLock.release(lockId);
                                acquired.set(false);
                                send(from, resp);
                            } catch (Exception e) {
                                try {
                                    if (acquired.get()) {
                                        distributedLock.release(lockId);
                                    }
                                } catch (Exception ie) {
                                    logger.error("Error unlocking after exception", ie);
                                }
                                resp.setBody(new ExceptionWrapper(e));
                                send(from, resp);
                            }
                            logger.trace("Handle ServiceCommandMessage({}) - Response Sent: {}", message.getCorrelationId(), resp);
                        });
                    } catch (Exception e) {
                        distributedLock.release(lockId);
                        throw e;
                    }
                }
                case QueryMessage<?> q -> {
                    logger.debug("Handle QueryMessage: {}", message);
                    var dest = peekMessageHandlerAddress(q.getQueryName());
                    var invocationStart = PerformanceStoreService.now();
                    logger.trace("Handle QueryMessage({}) - Forward Invocation: {}", message.getCorrelationId(), message);
                    forward(from, dest, message,
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
                                send(from, resp);
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
                            f.getLimit(),
                            handlerService.findAllHandledPayloadsNameByComponentName(f.getComponentName()));
                    var resp = new EventoResponse();
                    resp.setCorrelationId(message.getCorrelationId());
                    resp.setRequestTimestamp(message.getTimestamp());
                    resp.setTimeout(message.getTimeout());
                    resp.setUnit(message.getUnit());
                    resp.setBody(new EventFetchResponse(new ArrayList<>(events.stream().map(EventStoreEntry::toPublishedEvent).collect(Collectors.toList()))));
                    send(from, resp);

                }
                case EventLastSequenceNumberRequest ignored -> {
                    var resp = new EventoResponse();
                    resp.setCorrelationId(message.getCorrelationId());
                    resp.setRequestTimestamp(message.getTimestamp());
                    resp.setTimeout(message.getTimeout());
                    resp.setUnit(message.getUnit());
                    resp.setBody(new EventLastSequenceNumberResponse(eventStore.getLastEventSequenceNumber()));
                    send(from, resp);
                }
                case null, default ->
                        throw new IllegalArgumentException("Missing Handler for " + (request != null ? request.getClass() : null));
            }
        } catch (Throwable e) {
            logger.error("Error handling message in server", e);
            send(from, tw(message.getCorrelationId(), e));
        }
    }

    private void send(NodeAddress to, Serializable message) {
        long attempt = 0;
        while (attempt <= sendRetryMaxAttempts) {
            attempt++;
            if(message instanceof Expirable expirable){
                if(expirable.checkExpired()){
                    return;
                }
            }
            try {
                var out = view.get(to);
                if(out == null){
                    throw new RuntimeException("No valid connection found in view for node: %s (v%s) - %s. Attempt %d/%d".formatted(
                            to.bundleId(),
                            to.bundleVersion(),
                            to.instanceId(),
                            attempt,
                            sendRetryMaxAttempts
                    ));
                }
                synchronized (out) {
                        out.writeObject(message);
                        out.flush();
                }
                if(attempt > 1){
                    logger.warn("Message sent after {} attempts", attempt);
                }
                return;
            } catch (Exception e) {
                logger.warn("Message send over socket failed for bundle {} (v{}) and instance {}. Attempt {}/{} - {}",
                        to.bundleId(), to.bundleVersion(), to.instanceId(), attempt , sendRetryMaxAttempts, e.getMessage());

                // If this is the last attempt, throw the SendFailedException
                if (attempt >= sendRetryMaxAttempts) {
                    throw new RuntimeException(e);
                }

                logger.trace("Fail reason", e);

                // Sleep before the next retry
                try {
                    logger.warn("Sleeping before retry message {} to {} (v{}) {} ({})....",
                            message,
                            to.bundleId(),
                            to.bundleVersion(),
                            to.instanceId(),
                            sendRetryDelayMillis);
                    Thread.sleep(sendRetryDelayMillis);
                } catch (InterruptedException ignored) {
                    // Handle InterruptedException (if needed)
                }
            }
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

    private EventoResponse tw(String ci, Throwable e) {
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


    private void leave(NodeAddress address, Throwable reason) {
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


    public void forward(NodeAddress from, NodeAddress to,
                        EventoRequest eventoRequest,
                        Consumer<EventoResponse> response) {
        correlations.put(eventoRequest.getCorrelationId(), new Correlation(
                from, to,
                eventoRequest, response));
        try {
            send(to, eventoRequest);
        } catch (Throwable e) {
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
                distributedLock.acquire(lockId);
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
                distributedLock.release(lockId);
            }
        }
    }
}
