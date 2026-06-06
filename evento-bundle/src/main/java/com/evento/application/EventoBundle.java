package com.evento.application;

import com.evento.application.client.BundleClient;
import com.evento.application.client.BundleInboundDispatcher;
import com.evento.application.client.EventoServerAdapter;
import com.evento.application.client.admin.BundleAdminRequestHandler;
import com.evento.application.consumer.ConsumerHandle;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.application.consumer.DispatchContext;
import com.evento.application.consumer.EngineSupervisor;
import com.evento.application.consumer.ObserverEngine;
import com.evento.application.consumer.ProjectorEngine;
import com.evento.application.consumer.SagaEngine;
import com.evento.application.manager.*;
import com.evento.application.performance.TracingAgent;
import com.evento.application.performance.Track;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.modeling.messaging.message.internal.discovery.BundleConsumerRegistrationMessage;
import com.evento.transport.protocol.ProtocolPayloadTypes;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.CommandGatewayImpl;
import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.messaging.gateway.QueryGatewayImpl;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.messaging.message.application.*;
import com.evento.common.performance.PerformanceService;
import com.evento.common.performance.RemotePerformanceService;
import com.evento.common.serialization.ObjectMapperUtils;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The EventoBundle class represents a bundle of components and services related to event handling.
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
    private final EventoServer eventoServer;
    /**
     * Non-null after {@code start()} — the v2 consumer engine supervisor owns
     * all consumer loops (projector / saga / observer) on virtual-thread
     * executors. Admin surface is via {@link ConsumerHandle}, resolved through
     * {@link BundleAdminRequestHandler.ConsumerLookup}.
     */
    private final EngineSupervisor engineSupervisor;

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
            ObserverManager observerManager, TracingAgent tracingAgent,
            EventoServer eventoServer,
            EngineSupervisor engineSupervisor

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
        this.eventoServer = eventoServer;
        this.engineSupervisor = engineSupervisor;
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
            public Object invoke(Object self, Method method, Method proceed, Object[] args) throws Throwable {

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
                                    if(e instanceof InvocationTargetException ite){
                                        var t =  ite.getTargetException();
                                        t.setStackTrace(Arrays.stream(t.getStackTrace()).filter(s -> !s.getClassName().contains("_$$_")).toArray(StackTraceElement[]::new));
                                        throw t;
                                    }
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

        /**
         * The primary consumer path. When set, consumers run on
         * {@link EngineSupervisor} using the v2 SPI composition
         * (lock + checkpoint + DLQ + saga store + optional dedupe) wrapped by
         * {@link com.evento.common.messaging.consumer.ConsumerProcessor}.
         *
         * <p>If not set, defaults to {@link ConsumerEngineConfig#inMemory} at startup.
         */
        private BiFunction<EventoServer, PerformanceService, ConsumerEngineConfig> consumerEngineConfigBuilder;
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

        /** Repository browser base URL for source links, e.g. {@code https://github.com/org/repo/blob/main/my-bundle}. */
        private String repositoryUrl = "";
        /**
         * Line anchor prefix for the repository browser.
         * Use {@code "L"} for GitHub/GitLab, {@code "lines-"} for Bitbucket.
         */
        private String linePrefix = "L";
        /** Bundle short description (shown in dashboards). Falls back to bundleId if empty. */
        private String description = "";
        /** Bundle long-form markdown description. */
        private String detail = "";

        private TracingAgent tracingAgent;
        private MessageHandlerInterceptor messageHandlerInterceptor;

        private EventoServerMessageBusConfiguration eventoServerMessageBusConfiguration;

        private com.fasterxml.jackson.databind.ObjectMapper objectMapper = ObjectMapperUtils.getPayloadObjectMapper();
        private Map<String, Set<String>> contexts = new HashMap<>();
        private Consumer<EventoBundle> onEventoStartedHook = (eventoServer) -> {};

        /**
         * When {@code true}, start-up fails if the confinement check finds a
         * Command/Query gateway invocation outside a component class, or a
         * handler whose gateway payload type cannot be resolved statically.
         * Such call sites are invisible to the static extraction of the
         * interaction graph (the emit set under-approximates). When
         * {@code false} (the default), each finding is logged as a warning.
         */
        private boolean strictConfinement = false;

        public Builder setComponentContexts(Class<?> componentClass, String... contexts) {
            this.contexts.put(componentClass.getSimpleName(), new HashSet<>(Arrays.asList(contexts)));
            return this;
        }
        public Builder removeComponentContexts(Class<?> componentClass) {
            this.contexts.remove(componentClass.getSimpleName());
            return this;
        }

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
            if (bundleId == null || bundleId.isBlank()) {
                throw new IllegalArgumentException("Invalid bundleId");
            }
            if (eventoServerMessageBusConfiguration == null) {
                throw new IllegalArgumentException("Invalid messageBusConfiguration");
            }

            if (injector == null) {
                injector = clz -> null;
            }


            if (instanceId == null || instanceId.isBlank()) {
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
            if (messageHandlerInterceptor == null) {
                messageHandlerInterceptor = new LogTracesMessageHandlerInterceptor();
            }

            var aggregateManager = new AggregateManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, instanceId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent,
                    messageHandlerInterceptor
            );
            var serviceManager = new ServiceManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, instanceId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent,
                    messageHandlerInterceptor
            );
            var projectionManager = new ProjectionManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, instanceId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent,
                    messageHandlerInterceptor
            );
            var projectorManager = new ProjectorManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, instanceId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent,
                    sssFetchSize,
                    sssFetchDelay,
                    messageHandlerInterceptor
            );
            var sagaManager = new SagaManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, instanceId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent,
                    sssFetchSize,
                    sssFetchDelay,
                    messageHandlerInterceptor
            );
            var observerManager = new ObserverManager(
                    bundleId,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, instanceId, performanceService,
                            tracingAgent, c, p),
                    tracingAgent,
                    sssFetchSize,
                    sssFetchDelay,
                    messageHandlerInterceptor
            );
            var invokerManager = new InvokerManager();

            logger.info("Discovery handlers in %s".formatted(basePackage));
            // SubTypes is configured to keep every scanned type (not only types
            // with a scanned supertype) so the confinement check below can sweep
            // the whole package, component or not.
            Reflections reflections = new Reflections(new ConfigurationBuilder()
                    .forPackages(basePackage.getName())
                    .setScanners(Scanners.TypesAnnotated,
                            Scanners.SubTypes.filterResultsBy(c -> true)));

            aggregateManager.parse(reflections, injector);
            serviceManager.parse(reflections, injector);
            projectionManager.parse(reflections, injector);
            projectorManager.parse(reflections, injector);
            sagaManager.parse(reflections, injector);
            observerManager.parse(reflections, injector);
            invokerManager.parse(reflections);

            logger.info("Discovery Complete");

            // Confinement check: a gateway call outside a component class is
            // invisible to static extraction — the derived interaction graph
            // would silently under-approximate. Warn by default; reject when
            // strictConfinement is set.
            var componentClasses = new HashSet<Class<?>>();
            for (var annotation : List.of(
                    com.evento.common.modeling.annotations.component.Aggregate.class,
                    com.evento.common.modeling.annotations.component.Service.class,
                    com.evento.common.modeling.annotations.component.Projection.class,
                    com.evento.common.modeling.annotations.component.Projector.class,
                    com.evento.common.modeling.annotations.component.Saga.class,
                    com.evento.common.modeling.annotations.component.Observer.class,
                    com.evento.common.modeling.annotations.component.Invoker.class)) {
                componentClasses.addAll(reflections.getTypesAnnotatedWith(annotation));
            }
            var confinementViolations = ConfinementScanner.check(
                    reflections.getSubTypesOf(Object.class), componentClasses);
            for (var v : confinementViolations) {
                logger.warn("Confinement check: {}.{} (line {}) invokes the {} gateway "
                                + "outside any component class - this call site is invisible to "
                                + "static extraction and the interaction graph will under-approximate. "
                                + "Move the call into the component class that owns the decision.",
                        v.className(), v.methodName(), v.line(), v.kind());
            }
            if (strictConfinement && !confinementViolations.isEmpty()) {
                throw new IllegalStateException(("Confinement check failed: %d gateway "
                        + "invocation(s) outside component classes (see warnings above). "
                        + "Move the gateway calls into their components or unset "
                        + "strictConfinement.").formatted(confinementViolations.size()));
            }

            var meta = HandlerMetadataBuilder.build(aggregateManager, serviceManager,
                    projectionManager, projectorManager, observerManager, sagaManager,
                    invokerManager, strictConfinement);
            var handlers = meta.handlers();
            var payloadInfo = meta.payloadInfo();

            final AtomicReference<EventoBundle> eventoBundle = new AtomicReference<>();


            logger.info("Starting EventoApplication %s".formatted(bundleId));
            logger.info("Connecting to Evento Server (v2 wire)...");
            var clusterAddress = eventoServerMessageBusConfiguration.getAddresses().getFirst();
            var adminCodec = new AdminPayloadCodec();
            var dispatcher = new BundleInboundDispatcher(adminCodec,
                    aggregateManager, serviceManager, projectionManager);
            var adminHandler = new BundleAdminRequestHandler(adminCodec,
                    (id, type) -> {
                        var bundle = eventoBundle.get();
                        if (bundle == null) return Optional.empty();
                        return bundle.getEventConsumer(id, type);
                    });

            // The payload types this bundle handles — drives the server's routing
            // table. v2 routes on payloadType (simple class name), matching the
            // convention v1 declared in RegisteredHandler.handledPayload.
            var handlerPayloadTypes = new ArrayList<>(payloadInfo.keySet());

            var bundleClient = BundleClient.builder(bundleId, instanceId)
                    .host(clusterAddress.serverAddress())
                    .port(clusterAddress.serverPort())
                    .bundleVersion(String.valueOf(bundleVersion))
                    .description(description.isEmpty() ? bundleId : description)
                    .detail(detail)
                    .repositoryUrl(repositoryUrl)
                    .linePrefix(linePrefix)
                    .handlerPayloadTypes(handlerPayloadTypes)
                    .registeredHandlers(handlers)
                    .payloadInfo(payloadInfo)
                    // We send the enable notification manually after projector
                    // consumers have caught up to the head — matches v1 timing.
                    .autoEnable(false)
                    .build();

            bundleClient.registerRequestHandler(ProtocolPayloadTypes.SERVER_ADMIN_REQUEST, adminHandler);
            for (String payloadType : handlerPayloadTypes) {
                bundleClient.registerRequestHandler(payloadType, dispatcher);
            }
            bundleClient.start().get();

            var eventoServer = new EventoServerAdapter(
                    bundleClient, bundleId, instanceId, bundleVersion, adminCodec);

            if (commandGateway == null) {
                commandGateway = commandGatewayBuilder.apply(eventoServer);
            }
            if (queryGateway == null) {
                queryGateway = queryGatewayBuilder.apply(eventoServer);
            }
            if(performanceService == null) {
                performanceService = performanceServiceBuilder.apply(eventoServer);
            }

            // v2 is the only consumer path. Default to in-memory if not configured.
            if (consumerEngineConfigBuilder == null) {
                consumerEngineConfigBuilder = ConsumerEngineConfig::inMemory;
            }
            final var supervisor = new EngineSupervisor();

            eventoBundle.set(new EventoBundle(
                    basePackage.getName(),
                    bundleId, instanceId,
                    aggregateManager, projectionManager, sagaManager, commandGateway,
                    queryGateway,
                    performanceService,
                    serviceManager, projectorManager, observerManager, tracingAgent,
                    eventoServer,
                    supervisor));

            // Wire shutdown hook to stop v2 engines gracefully
            Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
                eventoBundle.get().stopV2Engines(Duration.ofSeconds(30));
            }));

            var dispatchContext = new DispatchContext(
                    tracingAgent,
                    (c, p) -> createGatewayTelemetryProxy(commandGateway, queryGateway, bundleId, instanceId,
                            performanceService, tracingAgent, c, p),
                    messageHandlerInterceptor
            );

            logger.info("Starting projector consumers...");
            var start = Instant.now();
            var wait = new Semaphore(0);
            var engineConfig = consumerEngineConfigBuilder.apply(eventoServer, performanceService);
            startProjectorEnginesV2(eventoBundle.get(), wait::release, engineConfig, contexts, supervisor, dispatchContext);
            final ConsumerEngineConfig engineConfigForLater = engineConfig;
            var startThread = new Thread(() -> {
                try {
                    wait.acquire();
                    logger.info("All Projector (v2) Consumers head Reached! (in {} millis)",
                            Instant.now().toEpochMilli() - start.toEpochMilli());
                    logger.info("Sending registration to enable the Bundle");
                    eventoServer.enable();
                    startSagaAndObserverEnginesV2(eventoBundle.get(), engineConfigForLater, contexts, supervisor, dispatchContext);
                    sendConsumerRegistrationV2(eventoServer, supervisor);
                    logger.info("Application Started!");
                    Thread.ofPlatform().start(() -> onEventoStartedHook.accept(eventoBundle.get()));
                } catch (InterruptedException e) {
                    // The startup thread was interrupted before it finished — almost
                    // always because the bundle is being stopped during startup.
                    Thread.currentThread().interrupt();
                    logger.info("Bundle startup interrupted before completion (likely shutdown)");
                } catch (Exception e) {
                    // A stop() that races startup tears the engine executor down, so
                    // submitting the saga/observer engines throws RejectedExecution.
                    // That is benign — never kill the host JVM for it (design rule:
                    // no System.exit anywhere; failures surface through callbacks).
                    if (supervisor.isShuttingDown()) {
                        logger.info("Bundle startup aborted because the bundle is shutting down");
                    } else {
                        logger.error("Error during startup", e);
                    }
                }
            });
            startThread.setName("Start Bundle Thread (v2)");
            startThread.start();

            return eventoBundle.get();

        }

        /** v2 consumer registration — reads consumer ids from {@link EngineSupervisor}. */
        private static void sendConsumerRegistrationV2(EventoServer eventoServer,
                                                       EngineSupervisor supervisor) throws Exception {
            var cr = new BundleConsumerRegistrationMessage();
            cr.setProjectorConsumers(new HashMap<>());
            for (var c : supervisor.getProjectorEngines()) {
                cr.getProjectorConsumers()
                        .computeIfAbsent(c.getProjectorName(), k -> new HashSet<>())
                        .add(c.getConsumerId());
            }
            cr.setSagaConsumers(new HashMap<>());
            for (var c : supervisor.getSagaEngines()) {
                cr.getSagaConsumers()
                        .computeIfAbsent(c.getSagaName(), k -> new HashSet<>())
                        .add(c.getConsumerId());
            }
            cr.setObserverConsumers(new HashMap<>());
            for (var c : supervisor.getObserverEngines()) {
                cr.getObserverConsumers()
                        .computeIfAbsent(c.getObserverName(), k -> new HashSet<>())
                        .add(c.getConsumerId());
            }
            eventoServer.send(cr);
        }

        private static void startProjectorEnginesV2(
                EventoBundle bundle,
                Runnable onAllHeadReached,
                ConsumerEngineConfig engineConfig,
                Map<String, Set<String>> contexts,
                EngineSupervisor supervisor,
                DispatchContext dispatchContext) {
            var references = bundle.getProjectorManager().getReferences();
            if (references.isEmpty()) {
                onAllHeadReached.run();
                return;
            }
            int total = references.stream()
                    .mapToInt(p -> contexts.getOrDefault(p.getComponentName(),
                            Set.of(com.evento.common.utils.Context.ALL)).size())
                    .sum();
            var counter = new java.util.concurrent.atomic.AtomicInteger(total);
            logger.info("Checking for projector event consumers (v2)");
            for (var projector : references) {
                var annotation = projector.getRef().getClass()
                        .getAnnotation(com.evento.common.modeling.annotations.component.Projector.class);
                var projectorName = projector.getRef().getClass().getSimpleName();
                var projectorVersion = annotation.version();
                for (var context : contexts.getOrDefault(projectorName, Set.of(com.evento.common.utils.Context.ALL))) {
                    logger.info("Starting v2 engine for Projector: {} - Version: {} - Context: {}",
                            projectorName, projectorVersion, context);
                    var engine = new ProjectorEngine(
                            bundle.getBundleId(),
                            projectorName,
                            projectorVersion,
                            context,
                            supervisor.shutdownSupplier(),
                            engineConfig.processor(),
                            engineConfig.stateStore(),
                            engineConfig.deadEventQueue(),
                            bundle.getProjectorManager().getHandlers(),
                            dispatchContext,
                            bundle.getProjectorManager().getSssFetchSize(),
                            bundle.getProjectorManager().getSssFetchDelay(),
                            counter,
                            onAllHeadReached);
                    supervisor.addProjector(engine);
                }
            }
            supervisor.startProjectorEngines();
        }

        private static void startSagaAndObserverEnginesV2(
                EventoBundle bundle,
                ConsumerEngineConfig engineConfig,
                Map<String, Set<String>> contexts,
                EngineSupervisor supervisor,
                DispatchContext dispatchContext) {
            for (var saga : bundle.getSagaManager().getReferences()) {
                var annotation = saga.getRef().getClass()
                        .getAnnotation(com.evento.common.modeling.annotations.component.Saga.class);
                var sagaName = saga.getRef().getClass().getSimpleName();
                var sagaVersion = annotation.version();
                for (var context : contexts.getOrDefault(sagaName, Set.of(com.evento.common.utils.Context.ALL))) {
                    logger.info("Starting v2 engine for Saga: {} - Version: {} - Context: {}",
                            sagaName, sagaVersion, context);
                    var engine = new SagaEngine(
                            bundle.getBundleId(),
                            sagaName,
                            sagaVersion,
                            context,
                            supervisor.shutdownSupplier(),
                            engineConfig.processor(),
                            engineConfig.stateStore(),
                            engineConfig.deadEventQueue(),
                            bundle.getSagaManager().getHandlers(),
                            dispatchContext,
                            bundle.getSagaManager().getSssFetchSize(),
                            bundle.getSagaManager().getSssFetchDelay());
                    supervisor.addSaga(engine);
                }
            }
            for (var observer : bundle.getObserverManager().getReferences()) {
                var annotation = observer.getRef().getClass()
                        .getAnnotation(com.evento.common.modeling.annotations.component.Observer.class);
                var observerName = observer.getRef().getClass().getSimpleName();
                var observerVersion = annotation.version();
                for (var context : contexts.getOrDefault(observerName, Set.of(com.evento.common.utils.Context.ALL))) {
                    logger.info("Starting v2 engine for Observer: {} - Version: {} - Context: {}",
                            observerName, observerVersion, context);
                    var engine = new ObserverEngine(
                            bundle.getBundleId(),
                            observerName,
                            observerVersion,
                            context,
                            supervisor.shutdownSupplier(),
                            engineConfig.processor(),
                            engineConfig.stateStore(),
                            engineConfig.deadEventQueue(),
                            bundle.getObserverManager().getHandlers(),
                            dispatchContext,
                            bundle.getObserverManager().getSssFetchSize(),
                            bundle.getObserverManager().getSssFetchDelay());
                    supervisor.addObserver(engine);
                }
            }
            supervisor.startSagaAndObserverEngines();
        }
    }

    /**
     * Admin lookup for the {@link BundleAdminRequestHandler.ConsumerLookup} SPI.
     * Delegates to the engine supervisor (v2 path — always active in v2.0).
     */
    private Optional<? extends ConsumerHandle> getEventConsumer(String consumerId, ComponentType componentType) {
        return engineSupervisor.findConsumer(consumerId, componentType);
    }

    /**
     * Request all v2 engines to stop and block until they do or the deadline
     * elapses.
     */
    public void stopV2Engines(Duration deadline) {
        if (engineSupervisor != null) {
            engineSupervisor.stop(deadline);
        }
    }
}
