package com.evento.application;

import com.evento.application.client.BundleClient;
import com.evento.application.client.BundleClientConfig;
import com.evento.application.client.BundleClientState;
import com.evento.application.client.BundleInboundDispatcher;
import com.evento.application.client.EventoServerAdapter;
import com.evento.application.client.admin.BundleAdminRequestHandler;
import com.evento.application.consumer.ConsumerHandle;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.application.consumer.ConsumerExecutorResolver;
import com.evento.application.consumer.ConsumerExecutorValidator;
import com.evento.application.consumer.ConsumerStatsPublisher;
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
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
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
import org.reflections.util.FilterBuilder;

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

    private static final Logger logger = LoggerFactory.getLogger(EventoBundle.class);
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

    /**
     * What this bundle measured itself: its requests' round trips by payload type and its
     * handlers' execution time by handler type (see {@link com.evento.application.client.metrics.BundleLatencies});
     * {@code null} when the bundle does not run over the v2 {@code BundleClient}.
     */
    public com.evento.application.client.metrics.BundleLatencies getLatencies() {
        return eventoServer instanceof com.evento.application.client.EventoServerAdapter adapter
                ? adapter.client().latencies() : null;
    }

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

        /**
         * The base package by name, for when no {@link Package} object is at hand (a Spring Boot
         * starter reading it from configuration). {@link #basePackage} wins when both are set.
         */
        private String basePackageName;
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

        /**
         * Where this bundle instance runs (e.g. {@code "EU"}), advertised to the
         * broker as the {@code zone:<ZONE>} Hello capability. A broker running the
         * {@code LOCALITY_PREFERRED} pick strategy routes this bundle's commands and
         * queries to handlers in the same zone when any is available, and to any
         * handler otherwise. Null (the default) advertises no zone.
         */
        private String zone;

        /**
         * Where this bundle records the outcomes of keyed service commands (G5): null (the
         * default) records nothing. With a store, a service command whose idempotency key
         * already has an outcome is answered with it and its handler is not run again
         * ({@code event=service_outcome_replayed}): the broker's retry after a handler ran but
         * its event could not be stored (append failed, server died before the append) no longer
         * repeats the handler's side effects. Use a store every instance of the service shares,
         * e.g. {@code JdbcServiceCommandOutcomeStore} on the service's own database; a handler
         * that records in its own transaction ({@code ServiceCommandOutcomes#recordWith}) is
         * exactly-once for its database writes.
         */
        private com.evento.common.messaging.outcome.ServiceCommandOutcomeStore serviceCommandOutcomeStore;

        private TracingAgent tracingAgent;
        private MessageHandlerInterceptor messageHandlerInterceptor;

        /**
         * Restrict polymorphic payload deserialization to the framework, JDK value types, the
         * {@link #basePackage} and {@link #allowedPayloadPackages} (see
         * {@link com.evento.common.serialization.PayloadTypeAllowlist}). Off by default in 2.x;
         * turn it on in production. Setting {@link #allowedPayloadPackages} turns it on too.
         */
        private boolean restrictPayloadTypes;

        /**
         * Packages, besides {@link #basePackage}, whose classes travel as payloads (commands,
         * events, queries, results, aggregate state), e.g. a shared API module.
         */
        private java.util.List<String> allowedPayloadPackages = java.util.List.of();

        private EventoServerMessageBusConfiguration eventoServerMessageBusConfiguration;

        /**
         * The shared secret the server requires when {@code evento.server.bus.auth-token} is set,
         * sent in the handshake. Null sends none (accepted only by a server without a token).
         */
        private String authToken;

        /**
         * Transport settings for the link to the broker (event loops, codec,
         * heartbeat, TLS, reconnect strategy). Null uses
         * {@link com.evento.transport.netty.NettyTransportConfig#defaults()}.
         * {@link #heartbeatReadIdle} and {@link #reconnectStrategy}, when set,
         * are applied on top of it.
         */
        private com.evento.transport.netty.NettyTransportConfig transportConfig;

        /**
         * How long the broker may stay silent before the link is declared dead
         * and the bundle fails over (default 45 s). It bounds how long a silent
         * partition — no RST, packets just vanish — goes unnoticed. The Ping
         * cadence is lowered to a third of it when it would not otherwise fit.
         */
        private Duration heartbeatReadIdle;

        /** Backoff between reconnect attempts; null keeps the transport config's (exponential, 0.5–30 s). */
        private com.evento.transport.reconnect.ReconnectStrategy reconnectStrategy;

        /**
         * Deadline for a request issued without an explicit timeout. Commands and
         * queries sent through the gateways carry their own; this covers the rest.
         */
        private Duration defaultRequestTimeout = Duration.ofSeconds(30);

        /** Cap on outstanding requests from this bundle; 0 disables the bound. */
        private int maxInFlightRequests = 2048;

        /**
         * Fail-back to the first address of {@link #eventoServerMessageBusConfiguration}
         * after a failover (N16). Null keeps the default: on whenever there is more
         * than one address. While attached to another address the bundle probes the
         * first one without registering there, and moves back once it has answered
         * every probe for {@link #failbackStabilityWindow}, after draining what is in
         * flight. {@code false} keeps the pre-2.5 behaviour: back only at the next
         * disconnect.
         */
        private Boolean failbackEnabled;

        /** How often the preferred address is probed while attached elsewhere; null keeps 5 s. */
        private Duration failbackProbeInterval;

        /**
         * How long the preferred address must answer every probe before the bundle
         * moves back; null keeps 30 s. A failed probe restarts it, so a flapping
         * server never gets the bundle back.
         */
        private Duration failbackStabilityWindow;

        /** Upper bound of the random per-instance delay added to the window; null keeps 10 s. */
        private Duration failbackJitter;

        /**
         * Bound on the wait for requests in flight before the switch; null keeps 10 s.
         * Outbound requests still pending then fail with {@code BrokerChangedException}.
         */
        private Duration failbackDrainTimeout;

        /**
         * Answer the queries this bundle handles itself, without the broker, once the
         * bundle is enabled (default true). The query keeps the handler-side path of a
         * remote one: codec, interceptors, tracing agent, handler and round-trip metrics.
         */
        private boolean localQueryShortCircuit = true;

        /**
         * Replies of this bundle's handlers of at least this many bytes are compressed for
         * callers that accept it (default 32 KiB); 0 never compresses.
         */
        private int compressionThreshold = com.evento.transport.codec.PayloadEncoding.DEFAULT_THRESHOLD;

        /** Whether this bundle's requests accept compressed replies (default true). */
        private boolean acceptCompressedReplies = true;

        /**
         * Offer this bundle's streamed query answers on direct connections (2.6), for
         * callers that ask for it ({@code QueryOptions.direct()}): the broker routes the
         * query and the data flows bundle to bundle. Null (default): streams go through
         * the broker, which relays them with flow control.
         */
        private com.evento.application.client.stream.DirectStreamConfig directStreamConfig;

        /** Target size of one frame of a streamed query answer (default 256 KiB). */
        private int streamFrameBytes = com.evento.application.client.BundleInboundDispatcher.DEFAULT_STREAM_FRAME_BYTES;

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

        /**
         * Executors available to {@code @EventHandler(executor = "...")}, by name.
         * Declared here for the same reason contexts are: it is deployment configuration,
         * not something the handler's bytecode can carry.
         */
        private Map<String, com.evento.common.messaging.consumer.ConsumerExecutor> consumerExecutors = new HashMap<>();

        /**
         * The history of this bundle's payload classes (2.6): each {@link com.evento.common.versioning.Upcaster}
         * turns a payload stored or sent with an older shape into the next one, before it is
         * deserialized. They apply to the events projectors, sagas and observers read (dead-event
         * retries included), to the events and snapshot an aggregate is rebuilt from, and to the
         * commands and queries this bundle receives. A step that renames a command or a query
         * also routes the old name here, so callers not yet upgraded keep reaching the handler.
         * Validated at start: one step per payload and revision, and no chain may loop.
         */
        private List<com.evento.common.versioning.Upcaster> upcasters = new ArrayList<>();

        /**
         * The event contexts a consumer component (projector, saga, observer) reads.
         * A component with no contexts set reads every context
         * ({@link com.evento.common.utils.Context#WILDCARD}, {@code "*"}).
         *
         * <p><b>One consumer engine per context.</b> The bundle starts one engine, with
         * its own consumer id ({@code bundleId_Component_version_context}), checkpoint
         * and lock, for each value given here, and each engine fetches only the events
         * of its context. So one generic component class can serve several regions
         * from configuration alone, e.g.
         * {@code setComponentContexts(OrderProjector.class, System.getenv("EVENTO_CONTEXTS").split(","))}
         * with {@code EVENTO_CONTEXTS=EUROPA,AFRICA} in one deployment and {@code CINA}
         * in another. Replicas running the same bundle id and context compete for that
         * one lane (one active at a time); a different context, or a different bundle
         * id, is a separate lane. Contexts are keyed by the component's simple class
         * name, so two classes with the same simple name in one bundle share them.
         *
         * <p><b>{@code "ALL"} is a context like any other.</b> Only {@code *} is a
         * wildcard. A component configured with {@code "ALL"} reads the events whose
         * context is literally {@code "ALL"} (for example cross-region catalogue or
         * price-list events), not every event. Beware that the deprecated
         * {@code Context.ALL} constant is the wildcard {@code "*"}, not {@code "ALL"}:
         * declare your own constant for a literal {@code "ALL"} context.
         *
         * <p>Each value is either an exact context name ({@code "SUD_AMERICA"}) or a
         * pattern in which {@code *} stands for any run of characters ({@code "*"}
         * reads every context). Nothing else is a wildcard: in particular {@code _}
         * and {@code %} are ordinary characters of a name. The server turns the
         * value into a SQL {@code LIKE} pattern; servers before the G15 fix did not
         * escape {@code _} / {@code %}, so on those a name like {@code SUD_AMERICA}
         * also matched {@code SUDXAMERICA}. Against such a server, either avoid
         * {@code _} and {@code %} in context names or check that no other context
         * name collides with the pattern.
         *
         * <p>Context is carried by events only ({@code Event#getContext}); commands
         * and queries have no framework-level context, so an aggregate that must
         * emit events in the caller's context has to copy it from a command field
         * into the event itself.
         */
        /** Adds steps to {@link #upcasters} (2.6). */
        public Builder addUpcasters(com.evento.common.versioning.Upcaster... steps) {
            var all = new ArrayList<>(upcasters);
            all.addAll(Arrays.asList(steps));
            this.upcasters = all;
            return this;
        }

        public Builder setComponentContexts(Class<?> componentClass, String... contexts) {
            this.contexts.put(componentClass.getSimpleName(), new HashSet<>(Arrays.asList(contexts)));
            return this;
        }
        public Builder removeComponentContexts(Class<?> componentClass) {
            this.contexts.remove(componentClass.getSimpleName());
            return this;
        }

        /**
         * Register an executor that {@code @EventHandler(executor = "...")} can name to run
         * in parallel instead of sequentially.
         *
         * <pre>{@code
         * .addConsumerExecutor(ConsumerExecutors.virtual("read-model", 64))
         * }</pre>
         *
         * <p>A name is a shared capacity budget across the whole bundle — every handler
         * naming it competes for the same permits. Handlers referencing an unregistered
         * name fail bundle start-up.
         *
         * @see com.evento.common.messaging.consumer.ConsumerExecutors
         */
        public Builder addConsumerExecutor(com.evento.common.messaging.consumer.ConsumerExecutor executor) {
            this.consumerExecutors.put(executor.name(), executor);
            return this;
        }

        public Builder removeConsumerExecutor(String name) {
            this.consumerExecutors.remove(name);
            return this;
        }

        /** Default checkpoint mode for every consumer that does not override it. */
        private com.evento.common.messaging.consumer.CheckpointMode checkpointMode =
                com.evento.common.messaging.consumer.CheckpointMode.ON_START;

        /** Per-component overrides, keyed by simple class name like {@link #contexts}. */
        private Map<String, com.evento.common.messaging.consumer.CheckpointMode> componentCheckpointModes =
                new HashMap<>();

        /**
         * When a parallel consumer persists its checkpoint. Defaults to
         * {@link com.evento.common.messaging.consumer.CheckpointMode#ON_START}.
         *
         * <p>{@code WATERMARK} restores at-least-once delivery by committing only the
         * highest contiguous <em>completed</em> sequence, at the cost of replaying the
         * in-flight window after a crash. It is a per-consumer property, not a per-handler
         * one — a consumer has a single checkpoint.
         */
        public Builder setCheckpointMode(com.evento.common.messaging.consumer.CheckpointMode mode) {
            this.checkpointMode = mode;
            return this;
        }

        /**
         * How often parallel-consumption counters are pushed to the server for Micrometer.
         * Only applies when the bundle registers at least one consumer executor.
         */
        private Duration consumerStatsInterval = Duration.ofSeconds(30);

        public Builder setConsumerStatsInterval(Duration interval) {
            this.consumerStatsInterval = interval;
            return this;
        }

        /** Override the checkpoint mode for one projector. */
        public Builder setComponentCheckpointMode(Class<?> componentClass,
                                                  com.evento.common.messaging.consumer.CheckpointMode mode) {
            this.componentCheckpointModes.put(componentClass.getSimpleName(), mode);
            return this;
        }

        /**
         * The Builder class represents a builder for constructing objects.
         */
        private Builder() {
        }

        /** {@link #transportConfig} (or the defaults) with the heartbeat / reconnect overrides applied. */
        com.evento.transport.netty.NettyTransportConfig effectiveTransportConfig() {
            var cfg = transportConfig == null
                    ? com.evento.transport.netty.NettyTransportConfig.defaults()
                    : transportConfig;
            if (heartbeatReadIdle != null) {
                var writeIdle = cfg.heartbeatWriteIdle();
                if (writeIdle.compareTo(heartbeatReadIdle) >= 0) {
                    writeIdle = heartbeatReadIdle.dividedBy(3);
                }
                cfg = cfg.withHeartbeat(writeIdle, heartbeatReadIdle);
            }
            if (reconnectStrategy != null) {
                cfg = cfg.withReconnectStrategy(reconnectStrategy);
            }
            return cfg;
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
            if (basePackage == null && (basePackageName == null || basePackageName.isBlank())) {
                throw new IllegalArgumentException("Invalid basePackage");
            }
            final String base = basePackage != null ? basePackage.getName() : basePackageName.trim();
            if (bundleId == null || bundleId.isBlank()) {
                throw new IllegalArgumentException("Invalid bundleId");
            }
            if (eventoServerMessageBusConfiguration == null) {
                throw new IllegalArgumentException("Invalid messageBusConfiguration");
            }
            com.evento.common.utils.JdkRuntimeCheck.warning().ifPresent(logger::warn);
            if (restrictPayloadTypes || !allowedPayloadPackages.isEmpty()) {
                var packages = new java.util.ArrayList<String>();
                packages.add(base);
                packages.addAll(allowedPayloadPackages);
                com.evento.common.serialization.PayloadTypeAllowlist.restrictTo(packages);
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
            serviceManager.setOutcomeStore(serviceCommandOutcomeStore);
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

            logger.info("Discovery handlers in %s".formatted(base));
            // SubTypes is configured to keep every scanned type (not only types
            // with a scanned supertype) so the confinement check below can sweep
            // the whole package, component or not.
            // forPackages only selects classpath roots that CONTAIN the package;
            // without the input filter every class sharing those roots would be
            // scanned too, so two bundles in one jar could never be isolated.
            Reflections reflections = new Reflections(new ConfigurationBuilder()
                    .forPackages(base)
                    .filterInputsBy(new FilterBuilder().includePackage(base))
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
            var handlers = new ArrayList<>(meta.handlers());
            var payloadInfo = meta.payloadInfo();
            // Built before connecting: a duplicate step or a loop fails the start, not a read.
            var upcasterChain = com.evento.common.versioning.Upcasters.of(upcasters);
            if (!upcasterChain.isEmpty()) {
                logger.info("Upcasters: {}", upcasterChain);
            }

            final AtomicReference<EventoBundle> eventoBundle = new AtomicReference<>();


            logger.info("Starting EventoApplication %s".formatted(bundleId));
            logger.info("Connecting to Evento Server (v2 wire)...");
            // Every configured address, in order: the first is the preferred broker,
            // the rest are failover targets.
            var endpoints = eventoServerMessageBusConfiguration.getAddresses().stream()
                    .map(a -> new BundleClientConfig.Endpoint(a.serverAddress(), a.serverPort()))
                    .toList();
            var adminCodec = new AdminPayloadCodec();
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
            // A command or query an upcaster renamed keeps reaching its handler under the former
            // name, which callers not yet upgraded still send: the handler is declared for it too,
            // so the broker routes and treats it alike (an aggregate command is decorated with the
            // history of its aggregate, a query is straight), and the dispatcher upcasts it on
            // arrival. Renamed events need nothing of the kind: consumers fetch every event.
            var aliases = upcasterChain.aliasesOf(handlerPayloadTypes);
            for (var alias : aliases.entrySet()) {
                for (var h : meta.handlers()) {
                    if (alias.getValue().equals(h.getHandledPayload())
                            && (h.getHandlerType() == com.evento.common.modeling.bundle.types.HandlerType.AggregateCommandHandler
                            || h.getHandlerType() == com.evento.common.modeling.bundle.types.HandlerType.CommandHandler
                            || h.getHandlerType() == com.evento.common.modeling.bundle.types.HandlerType.QueryHandler)) {
                        handlers.add(h.forPayload(alias.getKey()));
                        if (!handlerPayloadTypes.contains(alias.getKey())) handlerPayloadTypes.add(alias.getKey());
                        logger.info("Routing former payload name {} to the handler of {} in {}",
                                alias.getKey(), alias.getValue(), h.getComponentName());
                    }
                }
            }

            var bundleClientBuilder = BundleClient.builder(bundleId, instanceId)
                    .endpoints(endpoints)
                    .failbackProbeInterval(failbackProbeInterval)
                    .failbackStabilityWindow(failbackStabilityWindow)
                    .failbackJitter(failbackJitter)
                    .failbackDrainTimeout(failbackDrainTimeout);
            if (failbackEnabled != null) bundleClientBuilder.failbackEnabled(failbackEnabled);
            var bundleClient = bundleClientBuilder
                    .expectedServerInstanceId(eventoServerMessageBusConfiguration.getExpectedServerInstanceId())
                    .expectedClusterName(eventoServerMessageBusConfiguration.getExpectedClusterName())
                    .authToken(authToken)
                    .transportConfig(effectiveTransportConfig())
                    .defaultRequestTimeout(defaultRequestTimeout)
                    .maxInFlightRequests(maxInFlightRequests)
                    .zone(zone)
                    .localQueryShortCircuit(localQueryShortCircuit)
                    .compressionThreshold(compressionThreshold)
                    .acceptCompressedReplies(acceptCompressedReplies)
                    .directStreams(directStreamConfig)
                    .bundleVersion(String.valueOf(bundleVersion))
                    .description(description.isEmpty() ? bundleId : description)
                    .detail(detail)
                    .repositoryUrl(repositoryUrl)
                    .linePrefix(linePrefix)
                    .handlerPayloadTypes(handlerPayloadTypes)
                    .registeredHandlers(handlers)
                    .payloadInfo(payloadInfo)
                    // We send the enable notification manually: immediately when no
                    // projector gates startup, or once every projector annotated with
                    // @Projector(waitForHeadReached = true) has caught up to the head.
                    .autoEnable(false)
                    .build();

            var dispatcher = new BundleInboundDispatcher(adminCodec,
                    aggregateManager, serviceManager, projectionManager,
                    bundleClient.latencies().handlers());
            // Read-your-writes: a query carrying a consistency token waits for this
            // bundle's projectors (bound to the engines once they exist, below).
            dispatcher.setStreamFrameBytes(streamFrameBytes);
            dispatcher.setUpcasters(upcasterChain);
            dispatcher.setRevisionGuard(new com.evento.application.client.RevisionGuard(bundleClient::revisionsAccepted));
            final var enginesForGate = new AtomicReference<EngineSupervisor>();
            dispatcher.setConsistencyGate(new com.evento.application.consumer.ProjectorConsistencyGate(enginesForGate::get));
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
            enginesForGate.set(supervisor);

            eventoBundle.set(new EventoBundle(
                    base,
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
            engineConfig.processor().setUpcasters(upcasterChain);
            ConsumerExecutorValidator.validate(
                    eventoBundle.get().getProjectorManager().getReferences(),
                    eventoBundle.get().getObserverManager().getReferences(),
                    consumerExecutors);
            supervisor.registerConsumerExecutors(consumerExecutors.values());
            final int gatingConsumers = startProjectorEnginesV2(
                    eventoBundle.get(), wait::release, engineConfig, contexts, supervisor,
                    dispatchContext, consumerExecutors,
                    componentName -> componentCheckpointModes.getOrDefault(componentName, checkpointMode));
            final ConsumerEngineConfig engineConfigForLater = engineConfig;
            final var consumerExecutorsForLater = consumerExecutors;
            var startThread = new Thread(() -> {
                try {
                    wait.acquire();
                    if (gatingConsumers > 0) {
                        logger.info("All {} head-gating Projector Consumer(s) head Reached! (in {} millis)",
                                gatingConsumers,
                                Instant.now().toEpochMilli() - start.toEpochMilli());
                    }
                    logger.info("Sending registration to enable the Bundle");
                    eventoServer.enable();
                    startSagaAndObserverEnginesV2(eventoBundle.get(), engineConfigForLater, contexts, supervisor,
                            dispatchContext, consumerExecutorsForLater);
                    sendConsumerRegistrationV2(eventoServer, supervisor, bundleClient);
                    eventoBundle.get().startConsumerStatsPublisher(
                            new ConsumerStatsPublisher(eventoServer, supervisor,
                                    engineConfigForLater.processor(), consumerStatsInterval));
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
                                                       EngineSupervisor supervisor,
                                                       BundleClient bundleClient) throws Exception {
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
            // Consumer rows on the server exist only while this instance is known
            // to the cluster (they are cleared when the instance leaves, and a
            // fresh server database starts empty). A one-shot registration would
            // therefore leave this bundle's consumers invisible after any server
            // restart or connection blip until the bundle itself restarted —
            // re-send on every re-established session instead. Registration is
            // idempotent server-side (rows are keyed by instanceId + consumerId).
            // The listener goes in first: if the send below cannot go out (the
            // bundle is between sessions), the next READY sends it.
            bundleClient.onStateChange((from, to) -> {
                if (to == BundleClientState.READY) {
                    try {
                        eventoServer.send(cr);
                    } catch (Exception e) {
                        logger.warn("Consumer re-registration after reconnect failed", e);
                    }
                }
            });
            try {
                eventoServer.send(cr);
            } catch (Exception e) {
                // Not fatal to startup: throwing here used to skip the stats publisher
                // and the started hook for good (N16 review).
                logger.info("Consumer registration not sent now ({}); it is sent when the next session is READY",
                        e.getMessage());
            }
        }

        /**
         * @return the number of consumer instances that gate the bundle-enable
         *         ({@code @Projector(waitForHeadReached = true)}, one per context);
         *         when zero, {@code onAllHeadReached} has already been invoked.
         */
        private static int startProjectorEnginesV2(
                EventoBundle bundle,
                Runnable onAllHeadReached,
                ConsumerEngineConfig engineConfig,
                Map<String, Set<String>> contexts,
                EngineSupervisor supervisor,
                DispatchContext dispatchContext,
                Map<String, com.evento.common.messaging.consumer.ConsumerExecutor> consumerExecutors,
                java.util.function.Function<String, com.evento.common.messaging.consumer.CheckpointMode> checkpointModes) {
            var references = bundle.getProjectorManager().getReferences();
            if (references.isEmpty()) {
                onAllHeadReached.run();
                return 0;
            }
            // Only projectors that opted in via @Projector(waitForHeadReached = true)
            // gate the bundle-enable; the rest align in the background after start.
            int total = references.stream()
                    .filter(p -> p.getRef().getClass()
                            .getAnnotation(com.evento.common.modeling.annotations.component.Projector.class)
                            .waitForHeadReached())
                    .mapToInt(p -> contexts.getOrDefault(p.getComponentName(),
                            Set.of(com.evento.common.utils.Context.WILDCARD)).size())
                    .sum();
            var counter = new java.util.concurrent.atomic.AtomicInteger(total);
            logger.info("Checking for projector event consumers (v2)");
            for (var projector : references) {
                var annotation = projector.getRef().getClass()
                        .getAnnotation(com.evento.common.modeling.annotations.component.Projector.class);
                var projectorName = projector.getRef().getClass().getSimpleName();
                var projectorVersion = annotation.version();
                for (var context : contexts.getOrDefault(projectorName, Set.of(com.evento.common.utils.Context.WILDCARD))) {
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
                            onAllHeadReached,
                            ConsumerExecutorResolver.forProjector(
                                    bundle.getProjectorManager().getHandlers(),
                                    projectorName,
                                    consumerExecutors),
                            checkpointModes.apply(projectorName),
                            annotation.waitForHeadReached());
                    supervisor.addProjector(engine);
                }
            }
            supervisor.startProjectorEngines();
            if (total == 0) {
                logger.info("No projector requires head alignment (waitForHeadReached=false) "
                        + "- enabling the bundle immediately; projectors keep aligning in background");
                onAllHeadReached.run();
            }
            return total;
        }

        private static void startSagaAndObserverEnginesV2(
                EventoBundle bundle,
                ConsumerEngineConfig engineConfig,
                Map<String, Set<String>> contexts,
                EngineSupervisor supervisor,
                DispatchContext dispatchContext,
                Map<String, com.evento.common.messaging.consumer.ConsumerExecutor> consumerExecutors) {
            for (var saga : bundle.getSagaManager().getReferences()) {
                var annotation = saga.getRef().getClass()
                        .getAnnotation(com.evento.common.modeling.annotations.component.Saga.class);
                var sagaName = saga.getRef().getClass().getSimpleName();
                var sagaVersion = annotation.version();
                for (var context : contexts.getOrDefault(sagaName, Set.of(com.evento.common.utils.Context.WILDCARD))) {
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
                for (var context : contexts.getOrDefault(observerName, Set.of(com.evento.common.utils.Context.WILDCARD))) {
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
                            bundle.getObserverManager().getSssFetchDelay(),
                            ConsumerExecutorResolver.forObserver(
                                    bundle.getObserverManager().getHandlers(),
                                    observerName,
                                    consumerExecutors));
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
    private volatile ConsumerStatsPublisher consumerStatsPublisher;

    void startConsumerStatsPublisher(ConsumerStatsPublisher publisher) {
        this.consumerStatsPublisher = publisher;
        publisher.start();
    }

    /**
     * Graceful stop: leave the server's routing (no new requests reach this bundle), stop the
     * consumer engines within {@code deadline}, close the connection. For containers that own the
     * lifecycle (the Spring Boot starter); the JVM shutdown hook stops only the engines.
     * Idempotent.
     */
    public void stop(Duration deadline) {
        if (eventoServer instanceof EventoServerAdapter adapter) {
            try {
                adapter.disable();
            } catch (RuntimeException e) {
                logger.debug("event=bundle_stop_disable_failed cause={}", e.toString());
            }
        }
        stopV2Engines(deadline);
        if (eventoServer instanceof EventoServerAdapter adapter) {
            adapter.close();
        }
    }

    public void stopV2Engines(Duration deadline) {
        if (consumerStatsPublisher != null) {
            consumerStatsPublisher.close();
        }
        if (engineSupervisor != null) {
            engineSupervisor.stop(deadline);
        }
    }
}
