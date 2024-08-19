package com.evento.application;

import com.evento.application.consumer.EventConsumer;
import com.evento.application.manager.*;
import com.evento.application.performance.TracingAgent;
import com.evento.application.performance.Track;
import com.evento.common.modeling.messaging.message.internal.consumer.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.application.bus.EventoServerClient;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.documentation.Domain;
import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.messaging.consumer.impl.InMemoryConsumerStateStore;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.CommandGatewayImpl;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.messaging.gateway.QueryGatewayImpl;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.common.modeling.messaging.message.application.*;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.performance.AutoscalingProtocol;
import com.evento.common.performance.PerformanceService;
import com.evento.common.performance.RemotePerformanceService;
import com.evento.common.serialization.ObjectMapperUtils;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The EventoBundle class represents a bundle of components and services related to event handling.
 * It provides functionality for starting saga event consumers and projector event consumers.
 */
@Getter
public class EventoBundle {

    private static final Logger logger = LogManager.getLogger(EventoBundle.class);
    private final String basePackage;
    private final String bundleId;
    private final String instanceId;
    private final PerformanceService performanceService;
    private final AggregateManager aggregateManager;
    private final ServiceManager serviceManager;
    private final ProjectionManager projectionManager;
    private final ProjectorManager projectorManager;
    private final ObserverManager observerManager;
    private final SagaManager sagaManager;
    private final transient CommandGateway commandGateway;
    private final transient QueryGateway queryGateway;
    private final TracingAgent tracingAgent;

    private EventoBundle(
            String basePackage,
            String bundleId,
            String instanceId,
            AggregateManager aggregateManager,
            ProjectionManager projectionManager,
            SagaManager sagaManager,
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            PerformanceService performanceService,
            ServiceManager serviceManager, ProjectorManager projectorManager,
            ObserverManager observerManager, TracingAgent tracingAgent

    ) {
        this.basePackage = basePackage;
        this.bundleId = bundleId;
        this.instanceId = instanceId;
        this.aggregateManager = aggregateManager;
        this.projectionManager = projectionManager;
        this.sagaManager = sagaManager;
        this.performanceService = performanceService;
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
        this.serviceManager = serviceManager;
        this.projectorManager = projectorManager;
        this.tracingAgent = tracingAgent;
        this.observerManager = observerManager;
    }

    /**
     * Creates a GatewayTelemetryProxy with the provided parameters.
     *
     * @param commandGateway     The command gateway to proxy.
     * @param queryGateway       The query gateway to proxy.
     * @param bundleId           The bundle identifier.
     * @param performanceService The performance service for tracking metrics.
     * @param tracingAgent       The tracing agent for correlating and tracking.
     * @param componentName      The name of the component associated with the proxy.
     * @param handledMessage     The message being handled by the proxy.
     * @return The created GatewayTelemetryProxy instance.
     */
    private static GatewayTelemetryProxy createGatewayTelemetryProxy(
            CommandGateway commandGateway,
            QueryGateway queryGateway,
            String bundleId,
            String instanceId,
            PerformanceService performanceService,
            TracingAgent tracingAgent,
            String componentName, Message<?> handledMessage) {
        return new GatewayTelemetryProxy(commandGateway, queryGateway, bundleId, performanceService,
                componentName, handledMessage, tracingAgent, instanceId);
    }


    /**
     * Retrieves an instance of the specified InvokerWrapper class.
     *
     * @param invokerClass The class of the InvokerWrapper to retrieve.
     * @param <T>          The type of the InvokerWrapper.
     * @return An instance of the specified InvokerWrapper class.
     * @throws RuntimeException if an error occurs while creating the instance.
     */
    @SuppressWarnings("unchecked")
    public <T extends InvokerWrapper> T getInvoker(Class<T> invokerClass) {
        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(invokerClass);
        var h = new MethodHandler() {
            @Override
            public Object invoke(Object self, Method method, Method proceed, Object[] args) throws Exception {

                if (method.getDeclaredAnnotation(InvocationHandler.class) != null) {
                    var payload = new InvocationMessage(
                            invokerClass, method, args
                    );
                    var gProxy = createGatewayTelemetryProxy(
                            commandGateway,
                            queryGateway,
                            bundleId, instanceId,
                            performanceService,
                            tracingAgent,
                            invokerClass.getSimpleName(),
                            payload
                    );
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
                    return tracingAgent.track(payload, invokerClass.getSimpleName(),
                            method.getDeclaredAnnotation(Track.class),
                            () -> {
                                Object result;
                                try{
                                    result = proceed.invoke(target, args);
                                }catch (Exception e){
                                    gProxy.sendServiceTimeMetric(start);
                                    throw e;
                                }
                                if(result instanceof CompletableFuture<?> cf){
                                    result = cf.whenComplete((s,f) -> gProxy.sendServiceTimeMetric(start));
                                }else{
                                    gProxy.sendServiceTimeMetric(start);
                                }
                                return result;
                            });
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

    /**
     * Retrieves information about the application.
     *
     * @return An instance of the ApplicationInfo class containing the following information:
     *         - basePackage: The base package of the application.
     *         - bundleId: The bundle identifier of the application.
     *         - aggregateMessageHandlers: The set of aggregate message handlers in the application.
     *         - serviceMessageHandlers: The set of service message handlers in the application.
     *         - projectionMessageHandlers: The set of projection message handlers in the application.
     *         - projectorMessageHandlers: The set of projector message handlers in the application.
     *         - sagaMessageHandlers: The set of saga message handlers in the application.
     */
    public ApplicationInfo getAppInfo() {
        var info = new ApplicationInfo();
        info.basePackage = basePackage;
        info.bundleId = bundleId;
        info.aggregateMessageHandlers = aggregateManager.getHandlers().keySet();
        info.serviceMessageHandlers = serviceManager.getHandlers().keySet();
        info.projectionMessageHandlers = projectionManager.getHandlers().keySet();
        info.projectorMessageHandlers = projectorManager.getHandlers().keySet();
        info.sagaMessageHandlers = sagaManager.getHandlers().keySet();
        return info;
    }

    /**
     * Represents information about an application.
     */
    public static class ApplicationInfo {
        /**
         * Represents the base package that is used for scanning components in an application.
         */
        public String basePackage;
        /**
         * Represents the bundle identifier of an application.
         */
        public String bundleId;

        /**
         * Represents a set of aggregate message handlers.
         */
        public Set<String> aggregateMessageHandlers;
        /**
         * The set of service message handlers.
         */
        public Set<String> serviceMessageHandlers;
        /**
         * Represents a set of projection message handlers.
         */
        public Set<String> projectionMessageHandlers;
        /**
         * A set of available message handlers for the projector.
         */
        public Set<String> projectorMessageHandlers;
        /**
         * A set of string values representing the saga message handlers.
         *
         */
        public Set<String> sagaMessageHandlers;
    }

    /**
     * The Builder class is responsible for constructing an EventoBundle and starting the Evento application.
     */
    @Getter
    @Setter
    @Accessors(chain = true)
    public static class Builder {
        private Package basePackage;
        private String bundleId;
        private String instanceId;
        private long bundleVersion = 1;
        private Function<Class<?>, Object> injector;

        private Function<EventoServer, AutoscalingProtocol> autoscalingProtocolBuilder;
        private BiFunction<EventoServer, PerformanceService, ConsumerStateStore> consumerStateStoreBuilder;
        private Function<EventoServer, CommandGateway> commandGatewayBuilder  = CommandGatewayImpl::new;
        @Setter(AccessLevel.NONE)
        private CommandGateway commandGateway;

        private Function<EventoServer, QueryGateway> queryGatewayBuilder  = QueryGatewayImpl::new;
        @Setter(AccessLevel.NONE)
        private QueryGateway queryGateway;

        private Function<EventoServer, PerformanceService> performanceServiceBuilder = eventoServer -> new RemotePerformanceService(eventoServer, 1);
        @Setter(AccessLevel.NONE)
        private PerformanceService performanceService;

        private int sssFetchSize = 1000;
        private int sssFetchDelay = 1000;

        private TracingAgent tracingAgent;

        private EventoServerMessageBusConfiguration eventoServerMessageBusConfiguration;

        private ObjectMapper objectMapper = ObjectMapperUtils.getPayloadObjectMapper();
        private Map<String, Set<String>> contexts = new HashMap<>();
        private Consumer<EventoBundle> onEventoStartedHook = (eventoServer) -> {};

        /**
         * The Builder class represents a builder for constructing objects.
         */
        private Builder() {
        }

        /**
         * Returns a new instance of the Builder class.
         *
         * @return a new instance of the Builder class
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Starts the Evento Application.
         *
         * @return the EventoBundle representing the started application
         * @throws Exception if there is an error during initialization
         */
        public EventoBundle start() throws Exception {
            if (basePackage == null) {
                throw new IllegalArgumentException("Invalid basePackage");
            }
            if (bundleId == null || bundleId.isBlank() || bundleId.isEmpty()) {
                throw new IllegalArgumentException("Invalid bundleId");
            }
            if (eventoServerMessageBusConfiguration == null) {
                throw new IllegalArgumentException("Invalid messageBusConfiguration");
            }

            if (injector == null) {
                injector = clz -> null;
            }


            if (instanceId == null || instanceId.isBlank() || instanceId.isEmpty()) {
                instanceId = UUID.randomUUID().toString();
            }


            if (sssFetchSize < 1) {
                sssFetchSize = 1;
            }
            if (sssFetchDelay < 100) {
                sssFetchDelay = 100;
            }
            if (tracingAgent == null) {
                tracingAgent = new TracingAgent(bundleId, bundleVersion);
            }


            var isShuttingDown = new AtomicBoolean();

            var aggregateManager = new AggregateManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, instanceId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent
            );
            var serviceManager = new ServiceManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, instanceId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent
            );
            var projectionManager = new ProjectionManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, instanceId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent
            );
            var projectorManager = new ProjectorManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, instanceId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent,
                    isShuttingDown::get,
                    sssFetchSize,
                    sssFetchDelay
            );
            var sagaManager = new SagaManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, instanceId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent,
                    isShuttingDown::get,
                    sssFetchSize,
                    sssFetchDelay
            );
            var observerManager = new ObserverManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, instanceId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent,
                    isShuttingDown::get,
                    sssFetchSize,
                    sssFetchDelay
            );
            var invokerManager = new InvokerManager();

            logger.info("Discovery handlers in %s".formatted(basePackage));
            Reflections reflections = new Reflections((new ConfigurationBuilder().forPackages(basePackage.getName())));

            aggregateManager.parse(reflections, injector);
            serviceManager.parse(reflections, injector);
            projectionManager.parse(reflections, injector);
            projectorManager.parse(reflections, injector);
            sagaManager.parse(reflections, injector);
            observerManager.parse(reflections, injector);
            invokerManager.parse(reflections);

            logger.info("Discovery Complete");

            var handlers = new ArrayList<RegisteredHandler>();
            var payloads = new HashSet<Class<?>>();
            aggregateManager.getHandlers().forEach((k, v) -> {
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
            serviceManager.getHandlers().forEach((k, v) -> {
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
            projectorManager.getHandlers().forEach((k, v) -> v.forEach((k1, v1) -> {
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
            }));
            observerManager.getHandlers().forEach((k, v) -> v.forEach((k1, v1) -> {
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
                payloads.add(v1.getEventHandler(k).getParameterTypes()[0]);
            }));
            sagaManager.getHandlers().forEach((k, v) -> v.forEach((k1, v1) -> {
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
                payloads.add(v1.getSagaEventHandler(k).getParameterTypes()[0]);
            }));
            projectionManager.getHandlers().forEach((k, v) -> {
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
            handlers.addAll(invokerManager.getHandlers());
            ObjectMapper mapper = new ObjectMapper();
            JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);
            var payloadInfo = new HashMap<String, String[]>();
            for (Class<?> p : payloads) {
                if (p == null) continue;
                var info = new String[2];
                payloadInfo.put(p.getSimpleName(), info);
                try {
                    info[0] = mapper.writeValueAsString(schemaGen.generateSchema(p));
                } catch (Exception ignored) {
                }
                if (p.getAnnotation(Domain.class) != null) {
                    info[1] = p.getAnnotation(Domain.class).name();
                }
            }

            var registration = new BundleRegistration(
                    bundleId,
                    bundleVersion,
                    instanceId,
                    handlers,
                    payloadInfo
            );



            final AtomicReference<EventoBundle> eventoBundle = new AtomicReference<>();


            logger.info("Starting EventoApplication %s".formatted(bundleId));
            logger.info("Connecting to Evento Server...");
            var eventoServer =
                    new EventoServerClient.Builder(
                            registration,
                            objectMapper,
                            eventoServerMessageBusConfiguration.getAddresses(),
                            (body) -> switch (body) {
                                case DecoratedDomainCommandMessage cm -> aggregateManager.handle(cm);
                                case ServiceCommandMessage sm -> serviceManager.handle(sm);
                                case QueryMessage<?> qm -> projectionManager.handle(qm);
                                case ConsumerFetchStatusRequestMessage cr -> {
                                    var resp = new ConsumerFetchStatusResponseMessage();
                                    eventoBundle.get()
                                            .getEventConsumer(cr.getConsumerId(), cr.getComponentType())
                                            .ifPresent(c -> {
                                                try {
                                                    resp.setDeadEvents(c.getDeadEventQueue());
                                                    resp.setLastEventSequenceNumber(c.getLastConsumedEvent());
                                                } catch (Exception e) {
                                                    throw new RuntimeException(e);
                                                }
                                            });
                                    yield resp;
                                }
                                case ConsumerSetEventRetryRequestMessage cr -> {
                                    var resp = new ConsumerResponseMessage();
                                    resp.setSuccess(true);
                                    eventoBundle.get()
                                            .getEventConsumer(cr.getConsumerId(), cr.getComponentType())
                                            .ifPresent(c -> {
                                                try {
                                                    c.setDeadEventRetry(cr.getEventSequenceNumber(), cr.isRetry());
                                                } catch (Exception e) {
                                                    throw new RuntimeException(e);
                                                }
                                            });
                                    yield resp;
                                }
                                case ConsumerProcessDeadQueueRequestMessage cr -> {
                                    var resp = new ConsumerResponseMessage();
                                    resp.setSuccess(true);
                                    eventoBundle.get()
                                            .getEventConsumer(cr.getConsumerId(), cr.getComponentType())
                                            .ifPresent(c -> {
                                        try {
                                            c.consumeDeadEventQueue();
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    });

                                    yield resp;
                                }
                                case ConsumerDeleteDeadEventRequestMessage cr -> {
                                    var resp = new ConsumerResponseMessage();
                                    resp.setSuccess(true);
                                    eventoBundle.get()
                                            .getEventConsumer(cr.getConsumerId(), cr.getComponentType())
                                            .ifPresent(c -> {
                                        try {
                                            c.deleteDeadEvent(cr.getEventSequenceNumber());
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    });

                                    yield resp;
                                }
                                case null, default -> throw new RuntimeException("Invalid request body: " + body);
                            }
                    )
                            .setMaxReconnectAttempts(eventoServerMessageBusConfiguration.getMaxReconnectAttempts())
                            .setReconnectDelayMillis(eventoServerMessageBusConfiguration.getReconnectDelayMillis())
                            .setMaxDisableAttempts(eventoServerMessageBusConfiguration.getMaxDisableAttempts())
                            .setDisableDelayMillis(eventoServerMessageBusConfiguration.getDisableDelayMillis())
                            .setMaxRetryAttempts(eventoServerMessageBusConfiguration.getMaxRetryAttempts())
                            .setRetryDelayMillis(eventoServerMessageBusConfiguration.getRetryDelayMillis())
                            .connect();

            if(autoscalingProtocolBuilder == null){
                autoscalingProtocolBuilder = (e) -> new AutoscalingProtocol(e) {

                    @Override
                    public void arrival() {

                    }

                    @Override
                    public void departure() {

                    }
                };
            }
            var autoscalingProtocol = autoscalingProtocolBuilder.apply(eventoServer);
            logger.info("Autoscaling protocol: %s".formatted(autoscalingProtocol.getClass().getName()));
            tracingAgent.setAutoscalingProtocol(autoscalingProtocol);


            if (consumerStateStoreBuilder == null) {
                consumerStateStoreBuilder = InMemoryConsumerStateStore::new;
            }
            if (commandGateway == null) {
                commandGateway = commandGatewayBuilder.apply(eventoServer);
            }
            if (queryGateway == null) {
                queryGateway = queryGatewayBuilder.apply(eventoServer);
            }
            if(performanceService == null) {
                performanceService = performanceServiceBuilder.apply(eventoServer);
            }

            var css = consumerStateStoreBuilder.apply(eventoServer, performanceService);
            eventoBundle.set(new EventoBundle(
                    basePackage.getName(),
                    bundleId, instanceId,
                    aggregateManager, projectionManager, sagaManager, commandGateway,
                    queryGateway,
                    performanceService,
                    serviceManager, projectorManager, observerManager, tracingAgent));

            logger.info("Starting projector consumers...");
            var start = Instant.now();
            var wait = new Semaphore(0);
            eventoBundle.get().startProjectorEventConsumers(wait::release, css, contexts);
            var startThread = new Thread(() -> {
                try {
                    wait.acquire();
                    logger.info("All Projector Consumers head Reached! (in {} millis)", Instant.now().toEpochMilli() - start.toEpochMilli());
                    logger.info("Sending registration to enable the Bundle");
                    eventoServer.enable();
                    eventoBundle.get().startSagaEventConsumers(css, contexts);
                    eventoBundle.get().startObserverEventConsumers(css, contexts);
                    eventoServer.registerConsumers(eventoBundle.get());
                    logger.info("Application Started!");
                    Thread.startVirtualThread(() -> onEventoStartedHook.accept(eventoBundle.get()));
                }catch (Exception e){
                    logger.error("Error during startup", e);
                    System.exit(1);
                }
            });
            startThread.setName("Start Bundle Thread");
            startThread.start();
            return eventoBundle.get();

        }
    }

    private Optional<? extends EventConsumer> getEventConsumer(String consumerId, ComponentType componentType) {
        return switch (componentType){
            case Saga ->
                    getSagaManager().getSagaEventConsumers()
                            .stream().filter(c -> c.getConsumerId().equals(consumerId))
                            .findFirst();
            case Projector ->  getProjectorManager().getProjectorEvenConsumers()
                    .stream().filter(c -> c.getConsumerId().equals(consumerId))
                    .findFirst();
            case Observer -> getObserverManager().getObserverEventConsumers()
                    .stream().filter(c -> c.getConsumerId().equals(consumerId))
                    .findFirst();
            case null, default -> throw new RuntimeException("Invalid consumer fetch " + consumerId + " - " + componentType);
        };
    }

    /**
     * Starts the saga event consumers for the registered SagaReferences. Each SagaReference is checked for
     * the Saga annotation, and for each context specified in the annotation, a new SagaEventConsumer is created
     * and started in a new thread.
     *
     * @param consumerStateStore the consumer state store to track the state of event consumers
     * @param contexts the component contexts associations
     */
    private void startSagaEventConsumers(ConsumerStateStore consumerStateStore, Map<String, Set<String>> contexts) {
        sagaManager.startSagaEventConsumers(consumerStateStore, contexts);
    }


    /**
     * Starts the projector event consumers for the specified contexts.
     *
     * @param onAllHeadReached   The callback to be executed when all heads are reached.
     * @param consumerStateStore The consumer state store to track the state of event consumers.
     * @param contexts           The contexts for which the projector event consumers should be started.
     */
    private void startProjectorEventConsumers(Runnable onAllHeadReached, ConsumerStateStore consumerStateStore, Map<String,Set<String>> contexts) {
        projectorManager.startEventConsumers(onAllHeadReached, consumerStateStore, contexts);
    }


    /**
     * Starts the observer event consumers for the registered ObserverReferences. Each ObserverReference is checked for
     * the Observer annotation, and for each context specified in the annotation, a new ObserverEventConsumer is created
     * and started in a new thread.
     *
     * @param consumerStateStore The consumer state store to track the state of event consumers.
     * @param contexts the component contexts associations
     */
    private void startObserverEventConsumers(ConsumerStateStore consumerStateStore, Map<String, Set<String>> contexts) {
        observerManager.startEventConsumers(consumerStateStore, contexts);
    }
}
