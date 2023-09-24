package org.evento.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.application.consumer.ProjectorEvenConsumer;
import org.evento.application.consumer.SagaEventConsumer;
import org.evento.application.manager.*;
import org.evento.application.performance.TracingAgent;
import org.evento.application.performance.Track;
import org.evento.application.proxy.GatewayTelemetryProxy;
import org.evento.application.proxy.InvokerWrapper;
import org.evento.application.reference.*;
import org.evento.common.documentation.Domain;
import org.evento.common.messaging.bus.MessageBus;
import org.evento.common.messaging.consumer.ConsumerStateStore;
import org.evento.common.messaging.consumer.impl.InMemoryConsumerStateStore;
import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.messaging.gateway.CommandGatewayImpl;
import org.evento.common.messaging.gateway.QueryGateway;
import org.evento.common.messaging.gateway.QueryGatewayImpl;
import org.evento.common.modeling.annotations.component.Observer;
import org.evento.common.modeling.annotations.component.*;
import org.evento.common.modeling.annotations.handler.InvocationHandler;
import org.evento.common.modeling.annotations.handler.SagaEventHandler;
import org.evento.common.modeling.bundle.types.ComponentType;
import org.evento.common.modeling.bundle.types.HandlerType;
import org.evento.common.modeling.bundle.types.PayloadType;
import org.evento.common.modeling.messaging.message.application.*;
import org.evento.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryRequest;
import org.evento.common.modeling.messaging.message.internal.discovery.ClusterNodeApplicationDiscoveryResponse;
import org.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import org.evento.common.modeling.messaging.payload.DomainEvent;
import org.evento.common.modeling.messaging.query.Multiple;
import org.evento.common.performance.AutoscalingProtocol;
import org.evento.common.performance.PerformanceService;
import org.evento.common.performance.RemotePerformanceService;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class EventoBundle {

    private static final Logger logger = LogManager.getLogger(EventoBundle.class);
    private final String basePackage;
    private final String bundleId;
    private final long bundleVersion;
    private final MessageBus messageBus;
    private final PerformanceService performanceService;
    private final Function<Class<?>, Object> findInjectableObject;
    private final AggregateManager aggregateManager;
    private final ServiceManager serviceManager;
    private final ProjectionManager projectionManager;
    private final ProjectorManager projectorManager;
    private final SagaManager sagaManager;
    private final ObserverManager observerManager;
    private final InvokerManager invokerManager;
    private final transient CommandGateway commandGateway;
    private final transient QueryGateway queryGateway;
    private final TracingAgent tracingAgent;
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
            int sssFetchDelay,
            TracingAgent tracingAgent

    ) {
        this.messageBus = messageBus;
        this.basePackage = basePackage;
        this.bundleId = bundleId;
        this.bundleVersion = bundleVersion;
        this.performanceService = performanceService;
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
        this.findInjectableObject = findInjectableObject;
        this.tracingAgent = tracingAgent;
        aggregateManager = new AggregateManager(
                bundleId,
                this::createGatewayTelemetryProxy,
                tracingAgent
        );
        serviceManager = new ServiceManager(
                bundleId,
                this::createGatewayTelemetryProxy,
                tracingAgent
        );
        projectionManager = new ProjectionManager(
                bundleId,
                this::createGatewayTelemetryProxy,
                tracingAgent
        );
        projectorManager = new ProjectorManager(
                bundleId,
                this::createGatewayTelemetryProxy,
                tracingAgent,
                () -> isShuttingDown,
                consumerStateStore,
                sssFetchSize,
                sssFetchDelay
        );
        sagaManager = new SagaManager(
                bundleId,
                this::createGatewayTelemetryProxy,
                tracingAgent,
                () -> isShuttingDown,
                consumerStateStore,
                sssFetchSize,
                sssFetchDelay
        );
        observerManager = new ObserverManager(
                bundleId,
                this::createGatewayTelemetryProxy,
                tracingAgent,
                () -> isShuttingDown,
                consumerStateStore,
                sssFetchSize,
                sssFetchDelay
        );
        invokerManager = new InvokerManager();
        messageBus.setRequestReceiver((src, request, response) -> {
            try {
                autoscalingProtocol.arrival();
                if (request instanceof DecoratedDomainCommandMessage c) {
                    aggregateManager.handle(
                            c,
                            response);
                } else if (request instanceof ServiceCommandMessage c) {
                    serviceManager.handle(
                            c,
                            response
                    );
                } else if (request instanceof QueryMessage<?> q) {
                    projectionManager.handle(
                            q,
                            response
                    );
                } else if (request instanceof ClusterNodeApplicationDiscoveryRequest) {
                    generateDiscoveryResponse(response);
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
                    observerManager.handle(e);
                }
            } catch (Throwable e) {
                logger.error("Observer consumption failed", e);
            } finally {
                autoscalingProtocol.departure();
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> this.isShuttingDown = true));

    }

    private void generateDiscoveryResponse(MessageBus.MessageBusResponseSender response) {
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
        projectorManager.getHandlers().forEach((k, v) -> {
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
        observerManager.getHandlers().forEach((k, v) -> {
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
                payloads.add(v1.getEventHandler(k).getParameterTypes()[0]);
            });
        });
        sagaManager.getHandlers().forEach((k, v) -> {
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
                payloads.add(v1.getSagaEventHandler(k).getParameterTypes()[0]);
            });


        });
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
        response.sendResponse(new ClusterNodeApplicationDiscoveryResponse(
                bundleId,
                bundleVersion,
                handlers,
                payloadInfo
        ));
    }

    private GatewayTelemetryProxy createGatewayTelemetryProxy(String componentName, Message<?> handledMessage) {
        return new GatewayTelemetryProxy(commandGateway, queryGateway, bundleId, performanceService,
                componentName, handledMessage, tracingAgent);
    }


    private void parsePackage() throws InvocationTargetException, InstantiationException, IllegalAccessException {

        logger.info("Discovery handlers in %s".formatted(basePackage));
        Reflections reflections = new Reflections((new ConfigurationBuilder().forPackages(basePackage)));

        aggregateManager.parse(reflections, findInjectableObject);
        serviceManager.parse(reflections, findInjectableObject);
        projectionManager.parse(reflections, findInjectableObject);
        projectorManager.parse(reflections, findInjectableObject);
        sagaManager.parse(reflections, findInjectableObject);
        observerManager.parse(reflections, findInjectableObject);
        invokerManager.parse(reflections);

        logger.info("Discovery Complete");
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

    public <T extends InvokerWrapper> T getInvoker(Class<T> invokerClass) {
        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(invokerClass);
        var h = new MethodHandler() {
            @Override
            public Object invoke(Object self, Method method, Method proceed, Object[] args) throws Throwable {

                if (method.getDeclaredAnnotation(InvocationHandler.class) != null) {
                    var payload = new InvocationMessage(
                            invokerClass, method, args
                    );
                    var gProxy = createGatewayTelemetryProxy(invokerClass.getSimpleName(),
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
                                try {
                                    Object result = proceed.invoke(target, args);
                                    gProxy.sendServiceTimeMetric(start);
                                    return result;
                                } catch (InvocationTargetException e) {
                                    throw e.getTargetException();
                                }
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

    public CommandGateway getCommandGateway() {
        return commandGateway;
    }

    public QueryGateway getQueryGateway() {
        return queryGateway;
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
        private int sssFetchDelay = 1000;

        private int alignmentDelay = 3000;

        private TracingAgent tracingAgent;

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

        public Builder setTracingAgent(TracingAgent tracingAgent) {
            this.tracingAgent = tracingAgent;
            return this;
        }

        public int getAlignmentDelay() {
            return alignmentDelay;
        }

        public Builder setAlignmentDelay(int alignmentDelay) {
            this.alignmentDelay = alignmentDelay;
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
            if (commandGateway == null) {
                commandGateway = new CommandGatewayImpl(messageBus, serverName);
            }
            if (queryGateway == null) {
                queryGateway = new QueryGatewayImpl(messageBus, serverName);
            }
            if (performanceService == null) {
                performanceService = new RemotePerformanceService(messageBus, serverName);
            }
            if (consumerStateStore == null) {
                consumerStateStore = new InMemoryConsumerStateStore(messageBus, bundleId, serverName, performanceService);
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
                        sssFetchDelay,
                        tracingAgent);
                eventoBundle.parsePackage();
                messageBus.askStatus();
                logger.info("Sleeping for alignment...");
                Thread.sleep(alignmentDelay);
                logger.info("Starting projector consumers...");
                var start = Instant.now();
                eventoBundle.startProjectorEventConsumers(() -> {
                    try {
                        logger.info("Projector Consumers head Reached! (in " + (Instant.now().toEpochMilli() - start.toEpochMilli()) + " millis)");
                        logger.info("Enabling message bus");
                        messageBus.enableBus();
                        logger.info("Message bus enabled");
                        logger.info("Wait for discovery...");
                        Thread.sleep(alignmentDelay);
                        eventoBundle.startSagaEventConsumers();
                        logger.info("Application Started!");
                    } catch (Exception e) {
                        logger.error("Error during startup", e);
                        System.exit(1);
                    }
                });

                return eventoBundle;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void startSagaEventConsumers() {
        sagaManager.startSagaEventConsumers();
    }

    private void startProjectorEventConsumers(Runnable onHedReached) throws Exception {
        projectorManager.startEventConsumer(onHedReached);
    }
}
