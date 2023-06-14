package org.evento.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.proxy.InvokerWrapper;
import org.evento.application.reference.*;
import org.evento.common.messaging.gateway.CommandGatewayImpl;
import org.evento.common.messaging.gateway.QueryGatewayImpl;
import org.evento.common.modeling.annotations.component.*;
import org.evento.common.modeling.annotations.component.Observer;
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

import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class EventoBundle {

    private static final Logger logger = LogManager.getLogger(EventoBundle.class);

    private final String basePackage;
    private final String bundleId;
    private final MessageBus messageBus;
    private final ConsumerStateStore consumerStateStore;
    private final PerformanceService performanceService;

    private final Function<Class<?>, Object> findInjectableObject;

    private final HashMap<String, AggregateReference> aggregateMessageHandlers = new HashMap<>();
    private final HashMap<String, ServiceReference> serviceMessageHandlers = new HashMap<>();
    private final HashMap<String, ProjectionReference> projectionMessageHandlers = new HashMap<>();
    private final HashMap<String, HashMap<String, ProjectorReference>> projectorMessageHandlers = new HashMap<>();
    private final HashMap<String, HashMap<String, ObserverReference>> observerMessageHandlers = new HashMap<>();
    private final HashMap<String, HashMap<String, SagaReference>> sagaMessageHandlers = new HashMap<>();

    private final List<RegisteredHandler> invocationHandlers = new ArrayList<>();
    private final transient CommandGateway commandGateway;
    private final transient QueryGateway queryGateway;
    private final List<ProjectorReference> projectors = new ArrayList<>();
    private final List<ObserverReference> observers = new ArrayList<>();
    private final List<SagaReference> sagas = new ArrayList<>();

    private final int sssFetchSize;
    private final int sssFetchDelay;

    private boolean isShuttingDown = false;

    private EventoBundle(
            String basePackage,
            String bundleId,
            long bundleVersion,
            MessageBus messageBus,
            AutoscalingProtocol autoscalingProtocol,
            ConsumerStateStore consumerStateStore,
            Function<Class<?>, Object> findInjectableObject,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            PerformanceService performanceService,
            int sssFetchSize,
            int sssFetchDelay

    ) {

        this.messageBus = messageBus;
        this.basePackage = basePackage;
        this.bundleId = bundleId;
        this.performanceService = performanceService;
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
        this.consumerStateStore = consumerStateStore;
        this.findInjectableObject = findInjectableObject;
        this.sssFetchSize = sssFetchSize;
        this.sssFetchDelay = sssFetchDelay;

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
                    var proxy = createGatewayTelemetryProxy(
                            handler.getComponentName(),
                            c.getCommandMessage());
                    var event = handler.invoke(
                            c.getCommandMessage(),
                            envelope,
                            c.getEventStream(),
                            proxy,
                            proxy
                    );
                    response.sendResponse(
                            new DomainCommandResponseMessage(
                                    new DomainEventMessage(event),
                                    handler.getSnapshotFrequency() <= c.getEventStream().size() ?
                                            new SerializedAggregateState<>(envelope.getAggregateState()) : null
                            )
                    );
                    proxy.sendInvocationsMetric();

                } else if (request instanceof ServiceCommandMessage c) {
                    var handler = getServiceMessageHandlers().get(c.getCommandName());
                    if (handler == null)
                        throw new HandlerNotFoundException("No handler found for %s in %s"
                                .formatted(c.getCommandName(), getBundleId()));
                    var proxy = createGatewayTelemetryProxy(handler.getComponentName(), c);
                    var event = handler.invoke(
                            c,
                            proxy,
                            proxy
                    );
                    response.sendResponse(new ServiceEventMessage(event));
                    proxy.sendInvocationsMetric();


                } else if (request instanceof QueryMessage<?> q) {
                    var handler = getProjectionMessageHandlers().get(q.getQueryName());
                    if (handler == null)
                        throw new HandlerNotFoundException("No handler found for %s in %s".formatted(q.getQueryName(), getBundleId()));
                    var proxy = createGatewayTelemetryProxy(handler.getComponentName(), q);
                    var result = handler.invoke(
                            q,
                            proxy,
                            proxy
                    );
                    response.sendResponse(new SerializedQueryResponse<>(result));
                    proxy.sendInvocationsMetric();
                } else if (request instanceof ClusterNodeApplicationDiscoveryRequest d) {
                    var handlers = new ArrayList<RegisteredHandler>();
                    var payloads = new HashSet<Class<?>>();
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
                        payloads.add(v.getAggregateCommandHandler(k).getParameterTypes()[0]);
                        payloads.add(v.getAggregateCommandHandler(k).getReturnType());
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
                        payloads.add(v.getAggregateCommandHandler(k).getParameterTypes()[0]);
                        payloads.add(v.getAggregateCommandHandler(k).getReturnType());
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
                            payloads.add(v1.getEventHandler(k).getParameterTypes()[0]);
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
                            payloads.add(v1.getEventHandler(k1).getParameterTypes()[0]);
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
                            payloads.add(v1.getSagaEventHandler(k1).getParameterTypes()[0]);
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
                        payloads.add(v.getQueryHandler(k).getParameterTypes()[0]);
                        payloads.add(((Class<?>) ((ParameterizedType) v.getQueryHandler(k).getGenericReturnType()).getActualTypeArguments()[0]));
                    });
                    handlers.addAll(invocationHandlers);
                    ObjectMapper mapper = new ObjectMapper();
                    JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);
                    var schemas = new HashMap<String, String>();
                    for (Class<?> p : payloads) {
                        if (p == null) continue;
                        try {
                            schemas.put(p.getSimpleName(), mapper.writeValueAsString(schemaGen.generateSchema(p)));
                        } catch (Exception ignored) {
                        }
                    }
                    response.sendResponse(new ClusterNodeApplicationDiscoveryResponse(
                            bundleId,
                            bundleVersion,
                            handlers,
                            schemas
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
                        var proxy = createGatewayTelemetryProxy(observerReference.getComponentName(), e);
                        observerReference.invoke(e, proxy, proxy);
                        proxy.sendServiceTimeMetric(start);
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                autoscalingProtocol.departure();
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> this.isShuttingDown = true));

    }

    private GatewayTelemetryProxy createGatewayTelemetryProxy(String componentName, Message<?> handledMessage) {
        return new GatewayTelemetryProxy(commandGateway, queryGateway, bundleId, performanceService,
                componentName, handledMessage);
    }


    private void parsePackage() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {

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
        if (sagas.isEmpty()) return;
        logger.info("Starting saga consumers");
        logger.info("Checking for saga event consumers");
        for (SagaReference saga : sagas) {
            var sagaName = saga.getRef().getClass().getSimpleName();
            var sagaVersion = saga.getRef().getClass().getAnnotation(Saga.class).version();
            logger.info("Starting event consumer for Saga %s".formatted(sagaName));
            new Thread(() -> {
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
                            var proxy = createGatewayTelemetryProxy(handler.getComponentName(),
                                    publishedEvent.getEventMessage());
                            var resp = handler.invoke(
                                    publishedEvent.getEventMessage(),
                                    sagaState,
                                    proxy,
                                    proxy
                            );
                            proxy.sendInvocationsMetric();
                            return resp;
                        }, sssFetchSize);
                    } catch (Throwable e) {
                        logger.error(e);
                        e.printStackTrace();
                        hasError = true;
                    }
                    if (sssFetchSize - consumedEventCount > 10) {
                        try {
                            Thread.sleep(hasError ? sssFetchDelay : sssFetchSize - consumedEventCount);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

            }).start();
        }
    }

    private void startProjectorEventConsumers(Runnable onHeadReached) {
        if (projectors.isEmpty()) {
            onHeadReached.run();
            return;
        }
        ;
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
                                    var proxy = createGatewayTelemetryProxy(handler.getComponentName(),
                                            publishedEvent.getEventMessage());
                                    handler.begin();
                                    handler.invoke(
                                            publishedEvent.getEventMessage(),
                                            proxy,
                                            proxy
                                    );
                                    handler.commit();
                                    proxy.sendInvocationsMetric();
                                }, fetchSize);
                    } catch (Throwable e) {
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
                    if (!hasError && !headReached && consumedEventCount >= 0 && consumedEventCount < fetchSize) {
                        headReached = true;
                        var aligned = counter.incrementAndGet();
                        if (aligned == projectors.size()) {
                            onHeadReached.run();
                        }
                    }
                }

            }).start();
        }

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

    private static class GatewayInterceptor implements java.lang.reflect.InvocationHandler {

        private final GatewayTelemetryProxy proxy;

        private GatewayInterceptor(GatewayTelemetryProxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getCommandGateway")) {
                return proxy;
            }
            if (method.getName().equals("getQueryGateway")) {
                return proxy;
            }
            return method.invoke(proxy, args);
        }
    }

    private static class InvokerInterceptor implements java.lang.reflect.InvocationHandler {

        private final Class<? extends InvokerWrapper> invoker;
        private final EventoBundle eventoBundle;


        private InvokerInterceptor(Class<? extends InvokerWrapper> invoker, EventoBundle eventoBundle) {
            this.invoker = invoker;
            this.eventoBundle = eventoBundle;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaredAnnotation(InvocationHandler.class) != null) {
                var payload = invoker.getSimpleName() + "::" + method.getName();
                var gProxy = eventoBundle.createGatewayTelemetryProxy(invoker.getSimpleName(),
                        new InvocationMessage<>(payload));
                var target = Proxy.newProxyInstance(
                        invoker.getClassLoader(),
                        new Class[]{invoker},
                        new GatewayInterceptor(gProxy));
                var start = Instant.now();
                Object result = method.invoke(target, args);
                gProxy.sendServiceTimeMetric(start);
                return result;

            }

            return method.invoke(proxy, args);
        }
    }

    public <T extends InvokerWrapper> T getInvoker(Class<T> invokerClass) {
        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(invokerClass);
        var h = new MethodHandler() {
            @Override
            public Object invoke(Object self, Method method, Method proceed, Object[] args) throws Throwable {

                if (method.getDeclaredAnnotation(InvocationHandler.class) != null) {
                    var payload = invokerClass.getSimpleName() + "::" + method.getName();
                    var gProxy = createGatewayTelemetryProxy(invokerClass.getSimpleName(),
                            new InvocationMessage<>(payload));
                    ProxyFactory factory = new ProxyFactory();
                    factory.setSuperclass(invokerClass);
                    var target = factory.create(new Class<?>[0], new Object[]{},
                            (s, m, p, a) -> {
                                if (m.getName().equals("getCommandGateway")) {
                                    return gProxy;
                                }
                                if (m.getName().equals("getQueryGateway")) {
                                    return gProxy;
                                }
                                return p.invoke(s, a);
                            });
                    var start = Instant.now();
                    Object result = proceed.invoke(target, args);
                    gProxy.sendServiceTimeMetric(start);
                    return result;

                }

                return proceed.invoke(self, args);
            }
        };

        try {
            return (T) factory.create(new Class<?>[0], new Object[]{}, h);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }

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

    public static class Builder {
        private Package basePackage;
        private String bundleId;
        private long bundleVersion = 1;
        private String serverName;
        private MessageBus messageBus;
        private AutoscalingProtocol autoscalingProtocol;
        private ConsumerStateStore consumerStateStore;
        private Function<Class<?>, Object> injector;

        private CommandGateway commandGateway;

        private QueryGateway queryGateway;

        private PerformanceService performanceService;

        private int sssFetchSize = 1000;
        private int sssFetchDelay = 5000;

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder setBasePackage(Package basePackage) {
            this.basePackage = basePackage;
            return this;
        }

        public Builder setBundleId(String bundleId) {
            this.bundleId = bundleId;
            return this;
        }

        public Builder setBundleVersion(long bundleVersion) {
            this.bundleVersion = bundleVersion;
            return this;
        }

        public Builder setServerName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        public Builder setMessageBus(MessageBus messageBus) {
            this.messageBus = messageBus;
            return this;
        }

        public Builder setAutoscalingProtocol(AutoscalingProtocol autoscalingProtocol) {
            this.autoscalingProtocol = autoscalingProtocol;
            return this;
        }

        public Builder setInjector(Function<Class<?>, Object> injector) {
            this.injector = injector;
            return this;
        }

        public Builder setConsumerStateStore(ConsumerStateStore consumerStateStore) {
            this.consumerStateStore = consumerStateStore;
            return this;
        }

        public Builder setCommandGateway(CommandGateway commandGateway) {
            this.commandGateway = commandGateway;
            return this;
        }

        public Builder setQueryGateway(QueryGateway queryGateway) {
            this.queryGateway = queryGateway;
            return this;
        }

        public Builder setPerformanceService(PerformanceService performanceService) {
            this.performanceService = performanceService;
            return this;
        }

        public Builder setSssFetchSize(int sssFetchSize) {
            this.sssFetchSize = sssFetchSize;
            return this;
        }

        public Builder setSssFetchDelay(int sssFetchDelay) {
            this.sssFetchDelay = sssFetchDelay;
            return this;
        }

        public EventoBundle start() {
            if (basePackage == null) {
                throw new IllegalArgumentException("Invalid basePackage");
            }
            if (bundleId == null || bundleId.isBlank() || bundleId.isEmpty()) {
                throw new IllegalArgumentException("Invalid bundleId");
            }
            if (serverName == null || serverName.isBlank() || serverName.isEmpty()) {
                throw new IllegalArgumentException("Invalid serverName");
            }
            if (messageBus == null) {
                throw new IllegalArgumentException("Invalid messageBus");
            }
            if (autoscalingProtocol == null) {
                autoscalingProtocol = new AutoscalingProtocol(
                        messageBus, bundleId, serverName
                ) {
                    @Override
                    public void arrival() {

                    }

                    @Override
                    public void departure() {

                    }
                };
            }
            if (injector == null) {
                injector = clz -> null;
            }
            if (consumerStateStore == null) {
                consumerStateStore = new InMemoryConsumerStateStore(messageBus, bundleId, serverName);
            }
            if (commandGateway == null) {
                commandGateway = new CommandGatewayImpl(messageBus, serverName);
            }
            if (queryGateway == null) {
                queryGateway = new QueryGatewayImpl(messageBus, serverName);
            }
            if (performanceService == null) {
                performanceService = new PerformanceService(messageBus, serverName);
            }
            if (sssFetchSize < 1) {
                sssFetchSize = 1;
            }
            if (sssFetchDelay < 100) {
                sssFetchDelay = 100;
            }
            try {
                logger.info("Starting EventoApplication %s".formatted(bundleId));
                logger.info("Used message bus: %s".formatted(messageBus.getClass().getName()));
                logger.info("Autoscaling protocol: %s".formatted(autoscalingProtocol.getClass().getName()));
                EventoBundle eventoBundle = new EventoBundle(
                        basePackage.getName(),
                        bundleId,
                        bundleVersion,
                        messageBus,
                        autoscalingProtocol,
                        consumerStateStore,
                        injector,
                        commandGateway,
                        queryGateway,
                        performanceService,
                        sssFetchSize,
                        sssFetchDelay);
                eventoBundle.parsePackage();
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
                        eventoBundle.startSagaEventConsumers();
                        logger.info("Application Started!");
                    } catch (Exception e) {
                        logger.error(e);
                        System.exit(1);
                    }
                });

                return eventoBundle;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
