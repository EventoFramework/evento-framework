package org.evento.application;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.application.reference.*;
import org.evento.common.modeling.annotations.component.*;
import org.evento.common.modeling.messaging.message.application.*;
import org.evento.common.messaging.consumer.ConsumerStateStore;
import org.evento.common.messaging.consumer.impl.InMemoryConsumerStateStore;
import org.evento.common.modeling.annotations.handler.InvocationHandler;
import org.evento.common.modeling.annotations.handler.SagaEventHandler;
import org.evento.common.modeling.bundle.types.ComponentType;
import org.evento.common.modeling.bundle.types.HandlerType;
import org.evento.common.modeling.bundle.types.PayloadType;
import org.evento.common.modeling.exceptions.HandlerNotFoundException;
import org.evento.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryRequest;
import org.evento.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryResponse;
import org.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import org.evento.common.modeling.messaging.payload.DomainEvent;
import org.evento.common.modeling.messaging.query.Multiple;
import org.evento.common.modeling.messaging.query.SerializedQueryResponse;
import org.evento.common.modeling.state.SerializedAggregateState;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.QueryGateway;
import org.evento.common.performance.AutoscalingProtocol;
import org.evento.common.performance.PerformanceService;
import org.evento.common.utils.Inject;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class EventoBundle {

    private static final Logger logger = LogManager.getLogger(EventoBundle.class);

    private final String basePackage;
    private final String bundleId;
    private final MessageBus messageBus;
    private final String serverName;
    private final ConsumerStateStore consumerStateStore;
    private final long bundleVersion;
    private final PerformanceService performanceService;

    private HashMap<String, AggregateReference> aggregateMessageHandlers = new HashMap<>();
    private HashMap<String, ServiceReference> serviceMessageHandlers = new HashMap<>();
    private HashMap<String, ProjectionReference> projectionMessageHandlers = new HashMap<>();
    private HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers = new HashMap<>();
    private HashMap<String, HashMap<String, ObserverReference>> observerMessageHandlers = new HashMap<>();
    private HashMap<String, HashMap<String, SagaReference>> sagaMessageHandlers = new HashMap<>();

    private final List<RegisteredHandler> invocationHandlers = new ArrayList<>();
    private transient CommandGateway commandGateway;
    private transient QueryGateway queryGateway;
    private List<ProjectorReference> projectors = new ArrayList<>();
    private List<ObserverReference> observers = new ArrayList<>();
    private List<SagaReference> sagas = new ArrayList<>();

    private boolean isShuttingDown = false;

    private EventoBundle(
            String basePackage,
            String bundleId,
            long bundleVersion,
            String serverName,
            MessageBus messageBus,
            AutoscalingProtocol autoscalingProtocol,
            ConsumerStateStore consumerStateStore,
            boolean autorun,
            int minInstances,
            int maxInstances) {


        this.messageBus = messageBus;
        this.basePackage = basePackage;
        this.bundleId = bundleId;
        this.performanceService = new PerformanceService(messageBus, serverName);
        this.commandGateway = new CommandGateway(messageBus, serverName);
        this.queryGateway = new QueryGateway(messageBus, serverName);
        this.serverName = serverName;
        this.consumerStateStore = consumerStateStore;
        this.bundleVersion = bundleVersion;

        messageBus.setRequestReceiver((src, request, response) -> {
            try {
                autoscalingProtocol.arrival();
                if (request instanceof DecoratedDomainCommandMessage c) {
                    var handler = getAggregateMessageHandlers()
                            .get(c.getCommandMessage().getCommandName());
                    if (handler == null)
                        throw new HandlerNotFoundException("No handler found for %s in %s"
                                .formatted(c.getCommandMessage().getCommandName(), getBundleId()));
                    var envelope = new AggregateStateEnvelope(c.getSerializedAggregateState().getAggregateState());
                    var event = handler.invoke(
                            c.getCommandMessage(),
                            envelope,
                            c.getEventStream(),
                            commandGateway,
                            queryGateway
                    );
                    response.sendResponse(
                            new DomainCommandResponseMessage(
                                    new DomainEventMessage(event),
                                    handler.getSnapshotFrequency() <= c.getEventStream().size() ?
                                            new SerializedAggregateState<>(envelope.getAggregateState()) : null
                            )
                    );
                }
                else if (request instanceof ServiceCommandMessage c) {
                    var handler = getServiceMessageHandlers().get(c.getCommandName());
                    if (handler == null)
                        throw new HandlerNotFoundException("No handler found for %s in %s"
                                .formatted(c.getCommandName(), getBundleId()));
                    var event = handler.invoke(
                            c,
                            commandGateway,
                            queryGateway
                    );
                    response.sendResponse(new ServiceEventMessage(event));
                }
                else if (request instanceof QueryMessage<?> q) {
                    var handler = getProjectionMessageHandlers().get(q.getQueryName());
                    if (handler == null)
                        throw new HandlerNotFoundException("No handler found for %s in %s".formatted(q.getQueryName(), getBundleId()));
                    var result = handler.invoke(
                            q,
                            commandGateway,
                            queryGateway
                    );
                    response.sendResponse(new SerializedQueryResponse<>(result));
                }
                else if (request instanceof ClusterNodeApplicationDiscoveryRequest d) {
                    var handlers = new ArrayList<RegisteredHandler>();
                    aggregateMessageHandlers.forEach((k, v) -> {
                        var r = v.getAggregateCommandHandler(k).getReturnType().getSimpleName();
                        handlers.add(new RegisteredHandler(
                                ComponentType.Aggregate,
                                v.getRef().getClass().getSimpleName(),
                                HandlerType.AggregateCommandHandler,
                                PayloadType.DomainCommand,
                                k,
                                r,
                                false,
                                null
                        ));
                        var esh = v.getEventSourcingHandler(r);
                        if (esh != null) {
                            handlers.add(new RegisteredHandler(
                                    ComponentType.Aggregate,
                                    v.getRef().getClass().getSimpleName(),
                                    HandlerType.EventSourcingHandler,
                                    PayloadType.DomainEvent,
                                    r,
                                    null,
                                    false,
                                    null
                            ));

                        }

                    });
                    serviceMessageHandlers.forEach((k, v) -> {
                        var r = v.getAggregateCommandHandler(k).getReturnType().getSimpleName();
                        handlers.add(new RegisteredHandler(
                                ComponentType.Service,
                                v.getRef().getClass().getSimpleName(),
                                HandlerType.CommandHandler,
                                PayloadType.ServiceCommand,
                                k,
                                r.equals("void") ? null : r,
                                false,
                                null
                        ));
                    });
                    projectorMessageHandlers.forEach((k, v) -> {
                        v.forEach((k1, v1) -> {
                            handlers.add(new RegisteredHandler(
                                    ComponentType.Projector,
                                    v1.getRef().getClass().getSimpleName(),
                                    HandlerType.EventHandler,
                                    v1.getEventHandler(k).getParameterTypes()[0].getSuperclass().isAssignableFrom(DomainEvent.class) ? PayloadType.DomainEvent : PayloadType.ServiceEvent,
                                    k,
                                    null,
                                    false,
                                    null
                            ));
                        });


                    });
                    observerMessageHandlers.forEach((k, v) -> {
                        v.forEach((k1, v1) -> {
                            handlers.add(new RegisteredHandler(
                                    ComponentType.Observer,
                                    v1.getRef().getClass().getSimpleName(),
                                    HandlerType.EventHandler,
                                    v1.getEventHandler(k).getParameterTypes()[0].getSuperclass().isAssignableFrom(DomainEvent.class) ? PayloadType.DomainEvent : PayloadType.ServiceEvent,
                                    k,
                                    null,
                                    false,
                                    null
                            ));
                        });
                    });
                    sagaMessageHandlers.forEach((k, v) -> {
                        v.forEach((k1, v1) -> {
                            handlers.add(new RegisteredHandler(
                                    ComponentType.Saga,
                                    v1.getRef().getClass().getSimpleName(),
                                    HandlerType.SagaEventHandler,
                                    v1.getSagaEventHandler(k).getParameterTypes()[0].getSuperclass().isAssignableFrom(DomainEvent.class) ? PayloadType.DomainEvent : PayloadType.ServiceEvent,
                                    k,
                                    null,
                                    false,
                                    v1.getSagaEventHandler(k).getAnnotation(SagaEventHandler.class).associationProperty()
                            ));
                        });


                    });
                    projectionMessageHandlers.forEach((k, v) -> {
                        var r = v.getQueryHandler(k).getReturnType();
                        handlers.add(new RegisteredHandler(
                                ComponentType.Projection,
                                v.getRef().getClass().getSimpleName(),
                                HandlerType.QueryHandler,
                                PayloadType.Query,
                                k,
                                ((Class<?>) (((ParameterizedType) v.getQueryHandler(k).getGenericReturnType()).getActualTypeArguments()[0])).getSimpleName(),
                                r.isAssignableFrom(Multiple.class),
                                null
                        ));
                    });
                    handlers.addAll(invocationHandlers);
                    response.sendResponse(new ClusterNodeApplicationDiscoveryResponse(
                            bundleId,
                            bundleVersion,
                            autorun,
                            minInstances,
                            maxInstances,
                            handlers
                    ));
                } else {
                    throw new IllegalArgumentException("Request not found");
                }
            } catch (Throwable e) {
                response.sendError(e);
            } finally {
                autoscalingProtocol.departure();
            }

        });

        messageBus.setMessageReceiver((src, request) -> {
            try {
                autoscalingProtocol.arrival();
                if (request instanceof EventMessage<?> e) {
                    for (ObserverReference observerReference : observerMessageHandlers.get(e.getEventName()).values()) {
                        var start = Instant.now();
                        observerReference.invoke(e, commandGateway, queryGateway);
                        this.performanceService.sendPerformances(
                                bundleId,
                                observerReference.getRef().getClass().getSimpleName(),
                                e.getEventName(),
                                start
                        );
                    }
                }
            }catch (Throwable e){
                e.printStackTrace();
            }finally {
                autoscalingProtocol.departure();
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> this.isShuttingDown = true));

    }

    public static EventoBundle start(
            String basePackage,
            String bundleId,
            long bundleVersion,
            boolean autorun,
            int minInstances,
            int maxInstances,
            String serverName,
            MessageBus messageBus,
            AutoscalingProtocol autoscalingProtocol,
            ConsumerStateStore consumerStateStore) {
        return start(basePackage, bundleId, bundleVersion, autorun, minInstances, maxInstances,serverName, messageBus, autoscalingProtocol, consumerStateStore, clz -> null);
    }

    public static EventoBundle start(
            String basePackage,
            String bundleId,
            long bundleVersion,
            boolean autorun,
            int minInstances,
            int maxInstances,
            String serverName,
            MessageBus messageBus,
            AutoscalingProtocol autoscalingProtocol) {
        return start(basePackage, bundleId, bundleVersion, autorun, minInstances, maxInstances, serverName, messageBus, autoscalingProtocol, new InMemoryConsumerStateStore(messageBus, bundleId, serverName), clz -> null);
    }
    public static EventoBundle start(
            String basePackage,
            String bundleId,
            long bundleVersion,
            boolean autorun,
            int minInstances,
            int maxInstances,
            String serverName,
            MessageBus messageBus,
            AutoscalingProtocol autoscalingProtocol,
            Function<Class<?>, Object> findInjectableObject) {
        return start(basePackage, bundleId, bundleVersion, autorun, minInstances, maxInstances,serverName, messageBus, autoscalingProtocol, new InMemoryConsumerStateStore(messageBus, bundleId, serverName), findInjectableObject);
    }

    public static EventoBundle start(
            String basePackage,
            String bundleId,
            long bundleVersion,
            boolean autorun,
            int minInstances,
            int maxInstances,
            String serverName,
            MessageBus messageBus,
            AutoscalingProtocol autoscalingProtocol,
            ConsumerStateStore consumerStateStore,
            Function<Class<?>, Object> findInjectableObject) {


        try {
            logger.info("Starting EventoApplication %s".formatted(bundleId));
            logger.info("Used message bus: %s".formatted(messageBus.getClass().getName()));
            logger.info("Autoscaling protocol: %s".formatted(autoscalingProtocol.getClass().getName()));
            EventoBundle eventoBundle = new EventoBundle(basePackage, bundleId, bundleVersion, serverName, messageBus, autoscalingProtocol, consumerStateStore, autorun, minInstances, maxInstances);
            eventoBundle.parsePackage(findInjectableObject);
            logger.info("Sleeping for alignment...");
            Thread.sleep(3000);
            logger.info("Starting projector consumers...");
            var start = Instant.now();
            eventoBundle.startProjectorEventConsumers(() -> {
                try {
                    logger.info("Projector Consumers head Reached! (in " + (Instant.now().toEpochMilli() - start.toEpochMilli()) + " millis)");
                    logger.info("Enabling message bus");
                    messageBus.enableBus();
                    logger.info("Message bus enabled");
                    logger.info("Wait for discovery...");
                    Thread.sleep(3000);
                    logger.info("Starting saga consumers");
                    eventoBundle.startSagaEventConsumers();
                    logger.info("Application Started!");
                }catch (Exception e){
                    logger.error(e);
                    System.exit(1);
                }
            });

            return eventoBundle;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }



    private void parsePackage(Function<Class<?>, Object> findInjectableObject) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {

        logger.info("Discovery handlers in %s".formatted(basePackage));
        Reflections reflections = new Reflections((new ConfigurationBuilder().forPackages(basePackage)));
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Aggregate.class)) {
            var aggregateReference = new AggregateReference(createComponentInstance(aClass, findInjectableObject), aClass.getAnnotation(Aggregate.class).snapshotFrequency());
            for (String command : aggregateReference.getRegisteredCommands()) {
                aggregateMessageHandlers.put(command, aggregateReference);
                logger.info("Aggregate command handler for %s found in %s".formatted(command, aggregateReference.getRef().getClass().getName()));
            }
        }
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Service.class)) {
            var serviceReference = new ServiceReference(createComponentInstance(aClass, findInjectableObject));
            for (String command : serviceReference.getRegisteredCommands()) {
                serviceMessageHandlers.put(command, serviceReference);
                logger.info("Service command handler for %s found in %s".formatted(command, serviceReference.getRef().getClass().getName()));
            }
        }
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Projection.class)) {
            var projectionReference = new ProjectionReference(createComponentInstance(aClass, findInjectableObject));
            for (String query : projectionReference.getRegisteredQueries()) {
                projectionMessageHandlers.put(query, projectionReference);
                logger.info("Projection query handler for %s found in %s".formatted(query, projectionReference.getRef().getClass().getName()));
            }
        }
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Projector.class)) {
            var projectorReference = new ProjectorReference(createComponentInstance(aClass, findInjectableObject));
            projectors.add(projectorReference);
            for (String event : projectorReference.getRegisteredEvents()) {
                var hl = projectorMessageHandlers.getOrDefault(event, new HashMap<>());
                hl.put(aClass.getSimpleName(), projectorReference);
                projectorMessageHandlers.put(event, hl);
                logger.info("Projector event handler for %s found in %s".formatted(event, projectorReference.getRef().getClass().getName()));
            }
        }
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Observer.class)) {
            var observerReference = new ObserverReference(createComponentInstance(aClass, findInjectableObject));
            observers.add(observerReference);
            for (String event : observerReference.getRegisteredEvents()) {
                var hl = observerMessageHandlers.getOrDefault(event, new HashMap<>());
                hl.put(aClass.getSimpleName(), observerReference);
                observerMessageHandlers.put(event, hl);
                logger.info("Observer event handler for %s found in %s".formatted(event, observerReference.getRef().getClass().getName()));
            }
        }
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Saga.class)) {
            var sagaReference = new SagaReference(createComponentInstance(aClass, findInjectableObject));
            sagas.add(sagaReference);
            for (String event : sagaReference.getRegisteredEvents()) {
                var hl = sagaMessageHandlers.getOrDefault(event, new HashMap<>());
                hl.put(aClass.getSimpleName(), sagaReference);
                sagaMessageHandlers.put(event, hl);
                logger.info("Saga event handler for %s found in %s".formatted(event, sagaReference.getRef().getClass().getName()));
            }
        }
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(Invoker.class)) {
            for (Method declaredMethod : aClass.getDeclaredMethods()) {
                if (declaredMethod.getAnnotation(InvocationHandler.class) != null) {
                    var payload = aClass.getSimpleName() + "::" + declaredMethod.getName();
                    invocationHandlers.add(new RegisteredHandler(
                            ComponentType.Invoker,
                            aClass.getSimpleName(),
                            HandlerType.InvocationHandler,
                            PayloadType.Invocation,
                            payload,
                            null,
                            false,
                            null
                    ));
                    logger.info("Invoker invocation handler for %s found in %s".formatted(payload, aClass.getName()));

                }
            }
        }
        logger.info("Discovery Complete");
    }

    private void startSagaEventConsumers() {
        logger.info("Checking for saga event consumers");
        for (SagaReference saga : sagas) {
            var sagaName = saga.getRef().getClass().getSimpleName();
            var sagaVersion = saga.getRef().getClass().getAnnotation(Saga.class).version();
            logger.info("Starting event consumer for Saga %s".formatted(sagaName));
            new Thread(() -> {
                var fetchSize = 1000;
                var consumerId = bundleId + "_" + sagaVersion + "_" + sagaName;
                while (!isShuttingDown) {
                    var hasError = false;
                    var consumedEventCount = 0;
                    try {
                        consumedEventCount = consumerStateStore.consumeEventsForSaga(consumerId, sagaName, (sagaStateFetcher, publishedEvent) -> {
                            var handlers = getSagaMessageHandlers()
                                    .get(publishedEvent.getEventName());
                            if (handlers == null) return null;

                            var handler = handlers.getOrDefault(sagaName, null);
                            if (handler == null) return null;

                            var associationProperty = handler.getSagaEventHandler(publishedEvent.getEventName())
                                    .getAnnotation(SagaEventHandler.class).associationProperty();
                            var associationValue = publishedEvent.getEventMessage().getAssociationValue(associationProperty);

                            var sagaState = sagaStateFetcher.getLastState(
                                    sagaName,
                                    associationProperty,
                                    associationValue
                            );
                            return handler.invoke(
                                    publishedEvent.getEventMessage(),
                                    sagaState,
                                    commandGateway,
                                    queryGateway
                            );
                        }, fetchSize);
                    }catch (Throwable e){
                        logger.error(e);
                        e.printStackTrace();
                        hasError = true;
                    }
                    if (fetchSize - consumedEventCount > 10) {
                        try {
                            Thread.sleep(hasError ? 5000 : fetchSize - consumedEventCount);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

            }).start();
        }
    }
    public void startProjectorEventConsumers(Runnable onHeadReached) {
        var counter = new AtomicInteger();
        logger.info("Checking for projector event consumers");
        for (ProjectorReference projector : projectors) {
            var projectorName = projector.getRef().getClass().getSimpleName();
            var projectorVersion = projector.getRef().getClass().getAnnotation(Projector.class).version();
            logger.info("Starting event consumer for Projector %s".formatted(projectorName));
            new Thread(() -> {
                var headReached = false;
                var fetchSize = 1000;
                var consumerId = bundleId + "_" + projectorVersion + "_" + projectorName;
                while (!isShuttingDown) {
                    var hasError = false;
                    var consumedEventCount = 0;
                    try {
                        consumedEventCount = consumerStateStore.consumeEventsForProjector(
                                consumerId,
                                projectorName,
                                publishedEvent -> {
                            var handlers = getProjectorMessageHandlers()
                                    .get(publishedEvent.getEventName());
                            if (handlers == null) return;

                            var handler = handlers.getOrDefault(projectorName, null);
                            if (handler == null) return;

                            handler.begin();
                            handler.invoke(
                                    publishedEvent.getEventMessage(),
                                    commandGateway,
                                    queryGateway
                            );
                            handler.commit();
                        }, fetchSize);
                    }catch (Throwable e){
                        logger.error(e);
                        e.printStackTrace();
                        hasError = true;
                    }
                    if (fetchSize - consumedEventCount > 10) {
                        try {
                            Thread.sleep(hasError ? 5000 : fetchSize - consumedEventCount);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    if(!hasError && !headReached && consumedEventCount >= 0 && consumedEventCount < fetchSize){
                        headReached = true;
                        var aligned = counter.incrementAndGet();
                        if(aligned == projectors.size()){
                            onHeadReached.run();
                        }
                    }
                }

            }).start();
        }

        if(projectors.isEmpty()) onHeadReached.run();

    }

    private Object createComponentInstance(Class<?> aClass, Function<Class<?>, Object> findInjectableObject) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        var ref = aClass.getConstructor().newInstance();
        for (Field declaredField : aClass.getDeclaredFields()) {
            if (declaredField.getAnnotation(Inject.class) != null) {
                var oldAccessibility = declaredField.canAccess(ref);
                declaredField.setAccessible(true);
                declaredField.set(ref, findInjectableObject.apply(declaredField.getType()));
                declaredField.setAccessible(oldAccessibility);
            }
        }
        return ref;
    }


    public HashMap<String, AggregateReference> getAggregateMessageHandlers() {
        return aggregateMessageHandlers;
    }

    public HashMap<String, ServiceReference> getServiceMessageHandlers() {
        return serviceMessageHandlers;
    }

    public HashMap<String, ProjectionReference> getProjectionMessageHandlers() {
        return projectionMessageHandlers;
    }


    public HashMap<String, HashMap<String, ProjectorReference>> getProjectorMessageHandlers() {
        return projectorMessageHandlers;
    }

    public HashMap<String, HashMap<String, SagaReference>> getSagaMessageHandlers() {
        return sagaMessageHandlers;
    }

    public List<RegisteredHandler> getInvocationHandlers() {
        return invocationHandlers;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public String getBundleId() {
        return bundleId;
    }

    public void gracefulShutdown() {
        this.messageBus.gracefulShutdown();
    }

    public static class ApplicationInfo {
        public String basePackage;
        public String bundleId;
        public String clusterName;

        public Set<String> aggregateMessageHandlers;
        public Set<String> serviceMessageHandlers;
        public Set<String> projectionMessageHandlers;
        public Set<String> projectorMessageHandlers;
        public Set<String> sagaMessageHandlers;
    }

    public ApplicationInfo getAppInfo() {
        var info = new ApplicationInfo();
        info.basePackage = basePackage;
        info.bundleId = bundleId;
        info.aggregateMessageHandlers = aggregateMessageHandlers.keySet();
        info.serviceMessageHandlers = serviceMessageHandlers.keySet();
        info.projectionMessageHandlers = projectionMessageHandlers.keySet();
        info.projectorMessageHandlers = projectorMessageHandlers.keySet();
        info.sagaMessageHandlers = sagaMessageHandlers.keySet();
        return info;
    }

    public CommandGateway getCommandGateway() {
        return commandGateway;
    }

    public QueryGateway getQueryGateway() {
        return queryGateway;
    }
}