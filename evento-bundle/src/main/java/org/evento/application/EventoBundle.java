package org.evento.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.application.bus.EventoServerClient;
import org.evento.application.bus.MessageBusConfiguration;
import org.evento.application.manager.*;
import org.evento.application.performance.TracingAgent;
import org.evento.application.performance.Track;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.proxy.InvokerWrapper;
import org.evento.common.documentation.Domain;
import org.evento.common.messaging.bus.EventoServer;
import org.evento.common.messaging.consumer.ConsumerStateStore;
import org.evento.common.messaging.consumer.impl.InMemoryConsumerStateStore;
import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.CommandGatewayImpl;
import org.evento.common.messaging.gateway.QueryGateway;
import org.evento.common.messaging.gateway.QueryGatewayImpl;
import org.evento.common.modeling.annotations.handler.InvocationHandler;
import org.evento.common.modeling.annotations.handler.SagaEventHandler;
import org.evento.common.modeling.bundle.types.ComponentType;
import org.evento.common.modeling.bundle.types.HandlerType;
import org.evento.common.modeling.bundle.types.PayloadType;
import org.evento.common.modeling.messaging.message.application.*;
import org.evento.common.modeling.messaging.message.internal.discovery.BundleRegistration;
import org.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import org.evento.common.modeling.messaging.payload.DomainEvent;
import org.evento.common.modeling.messaging.query.Multiple;
import org.evento.common.performance.AutoscalingProtocol;
import org.evento.common.performance.PerformanceService;
import org.evento.common.performance.RemotePerformanceService;
import org.evento.common.serialization.ObjectMapperUtils;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
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
            PerformanceService performanceService,
            TracingAgent tracingAgent,
            String componentName, Message<?> handledMessage) {
        return new GatewayTelemetryProxy(commandGateway, queryGateway, bundleId, performanceService,
                componentName, handledMessage, tracingAgent);
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
                            bundleId,
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
                                Object result = proceed.invoke(target, args);
                                gProxy.sendServiceTimeMetric(start);
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
    public static class ApplicationInfo {
        public String basePackage;
        public String bundleId;

        public Set<String> aggregateMessageHandlers;
        public Set<String> serviceMessageHandlers;
        public Set<String> projectionMessageHandlers;
        public Set<String> projectorMessageHandlers;
        public Set<String> sagaMessageHandlers;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class Builder {
        private Package basePackage;
        private String bundleId;
        private long bundleVersion = 1;
        private Function<Class<?>, Object> injector;

        private Function<EventoServer, AutoscalingProtocol> autoscalingProtocolBuilder;
        private BiFunction<EventoServer, PerformanceService, ConsumerStateStore> consumerStateStoreBuilder;
        private Function<EventoServer, CommandGateway> commandGatewayBuilder  = CommandGatewayImpl::new;
        private CommandGateway commandGateway;

        private Function<EventoServer, QueryGateway> queryGatewayBuilder  = QueryGatewayImpl::new;
        private QueryGateway queryGateway;

        private Function<EventoServer, PerformanceService> performanceServiceBuilder = eventoServer -> new RemotePerformanceService(eventoServer, 0.01);
        private PerformanceService performanceService;

        private int sssFetchSize = 1000;
        private int sssFetchDelay = 1000;

        private int alignmentDelay = 3000;

        private TracingAgent tracingAgent;

        private MessageBusConfiguration messageBusConfiguration;

        private ObjectMapper objectMapper = ObjectMapperUtils.getPayloadObjectMapper();

        private Builder() {
        }

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
            if (messageBusConfiguration == null) {
                throw new IllegalArgumentException("Invalid messageBusConfiguration");
            }

            if (injector == null) {
                injector = clz -> null;
            }


            if (sssFetchSize < 1) {
                sssFetchSize = 1;
            }
            if (sssFetchDelay < 100) {
                sssFetchDelay = 100;
            }
            if (alignmentDelay < 0) {
                alignmentDelay = 3000;
            }
            if (tracingAgent == null) {
                tracingAgent = new TracingAgent(bundleId, bundleVersion);
            }


            var isShuttingDown = new AtomicBoolean();

            String bundleInstance = UUID.randomUUID().toString();

            var aggregateManager = new AggregateManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent
            );
            var serviceManager = new ServiceManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent
            );
            var projectionManager = new ProjectionManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent
            );
            var projectorManager = new ProjectorManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent,
                    isShuttingDown::get,
                    sssFetchSize,
                    sssFetchDelay
            );
            var sagaManager = new SagaManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent,
                    isShuttingDown::get,
                    sssFetchSize,
                    sssFetchDelay
            );
            var observerManager = new ObserverManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, performanceService,
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
                    bundleInstance,
                    handlers,
                    payloadInfo
            );

            logger.info("Starting EventoApplication %s".formatted(bundleId));
            logger.info("Connecting to Evento Server...");
            var eventoServer =
                    new EventoServerClient.Builder(
                            registration,
                            objectMapper,
                            messageBusConfiguration.getAddresses(),
                            (body) -> {
                                if (body instanceof DecoratedDomainCommandMessage cm) {
                                    return aggregateManager.handle(cm);
                                } else if (body instanceof ServiceCommandMessage sm) {
                                    return serviceManager.handle(sm);
                                } else if (body instanceof QueryMessage<?> qm) {
                                    return projectionManager.handle(qm);
                                } else {
                                    throw new RuntimeException("Invalid request body: " + body);
                                }
                            }
                    )
                            .setMaxReconnectAttempts(messageBusConfiguration.getMaxReconnectAttempts())
                            .setReconnectDelayMillis(messageBusConfiguration.getReconnectDelayMillis())
                            .setMaxDisableAttempts(messageBusConfiguration.getMaxDisableAttempts())
                            .setDisableDelayMillis(messageBusConfiguration.getDisableDelayMillis())
                            .setMaxRetryAttempts(messageBusConfiguration.getMaxRetryAttempts())
                            .setRetryDelayMillis(messageBusConfiguration.getRetryDelayMillis())
                            .connect();


            if (autoscalingProtocolBuilder == null) {
                autoscalingProtocolBuilder = (e) -> new AutoscalingProtocol(
                        eventoServer
                ) {
                    @Override
                    public void arrival() {

                    }

                    @Override
                    public void departure() {

                    }
                };
            }
            if(performanceService == null) {
                performanceService = performanceServiceBuilder.apply(eventoServer);
            }
            if (consumerStateStoreBuilder == null) {
                consumerStateStoreBuilder = InMemoryConsumerStateStore::new;
            }
            if (commandGateway == null) {
                commandGateway = commandGatewayBuilder.apply(eventoServer);
            }
            if (queryGateway == null) {
                queryGateway = queryGatewayBuilder.apply(eventoServer);
            }

            logger.info("Autoscaling protocol: %s".formatted(autoscalingProtocolBuilder.getClass().getName()));
            var asp = autoscalingProtocolBuilder.apply(eventoServer);
            var css = consumerStateStoreBuilder.apply(eventoServer, performanceService);
            EventoBundle eventoBundle = new EventoBundle(
                    basePackage.getName(),
                    bundleId,
                    aggregateManager, projectionManager, sagaManager, commandGateway,
                    queryGateway,
                    performanceService,
                    serviceManager, projectorManager, observerManager, tracingAgent);
            logger.info("Starting projector consumers...");
            var start = Instant.now();
            eventoBundle.startProjectorEventConsumers(() -> {
                try {
                    logger.info("Projector Consumers head Reached! (in " + (Instant.now().toEpochMilli() - start.toEpochMilli()) + " millis)");
                    logger.info("Sending registration to enable the Bundle");
                    eventoServer.enable();
                    eventoBundle.startSagaEventConsumers(css);
                    eventoBundle.startObserverEventConsumers(css);
                    logger.info("Application Started!");
                } catch (Exception e) {
                    logger.error("Error during startup", e);
                    System.exit(1);
                }
            }, css);

            return eventoBundle;

        }
    }

    /**
     * Starts the saga event consumers for the registered SagaReferences. Each SagaReference is checked for
     * the Saga annotation, and for each context specified in the annotation, a new SagaEventConsumer is created
     * and started in a new thread.
     *
     * @param consumerStateStore the consumer state store to track the state of event consumers
     */
    private void startSagaEventConsumers(ConsumerStateStore consumerStateStore) {
        sagaManager.startSagaEventConsumers(consumerStateStore);
    }

    /**
     * Starts the event consumers for the projector.
     *
     * @param onHedReached       a Runnable that will be executed when the head is reached
     * @param consumerStateStore the ConsumerStateStore to use for tracking consumer state
     */
    private void startProjectorEventConsumers(Runnable onHedReached, ConsumerStateStore consumerStateStore) {
        projectorManager.startEventConsumers(onHedReached, consumerStateStore);
    }


    /**
     * Starts the observer event consumers for the registered ObserverReferences. Each ObserverReference is checked for
     * the Observer annotation, and for each context specified in the annotation, a new ObserverEventConsumer is created
     * and started in a new thread.
     *
     * @param consumerStateStore  The consumer state store to track the state of event consumers.
     */
    private void startObserverEventConsumers(ConsumerStateStore consumerStateStore) {
        observerManager.startEventConsumers(consumerStateStore);
    }
}
