package com.evento.server.es.pg;

import com.evento.application.client.BundleClient;
import com.evento.application.client.EventoServerAdapter;
import com.evento.application.performance.TracingAgent;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.CommandGatewayImpl;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.application.DecoratedDomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.DomainCommandResponseMessage;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.ServiceCommandMessage;
import com.evento.common.modeling.messaging.message.application.ServiceEventMessage;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.common.modeling.messaging.payload.DomainCommand;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.messaging.payload.ServiceCommand;
import com.evento.common.modeling.messaging.payload.ServiceEvent;
import com.evento.common.utils.PgDistributedLock;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.correlation.ForwardingDedupCache;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.bus.security.TokenValidator;
import com.evento.server.es.CommandBrokerHandler;
import com.evento.server.es.EventStore;
import com.evento.server.es.EventStoreMode;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.RequestTimeoutException;
import com.evento.transport.codec.JacksonCborCodec;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * G5 over real TCP and a real Postgres: two evento-servers sharing one event store, each with its
 * own aggregate and service handler bundle and its own callers, exactly as a two-cell cluster.
 *
 * <p>Every handler puts a fresh nonce in its event, so a response that carries the first
 * execution's nonce is the stored event replayed, and a different nonce is a second execution.
 * Handler invocations are counted across both servers.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class IdempotentCommandPostgresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = PostgresItSupport.newContainer();

    private static final String AGG_COMMAND = payloadName(IdemCmd.class);
    private static final String SERVICE_COMMAND = payloadName(IdemServiceCmd.class);

    private final AdminPayloadCodec codec = new AdminPayloadCodec();
    private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();
    private final AtomicInteger aggregateCalls = new AtomicInteger();
    private final AtomicInteger serviceCalls = new AtomicInteger();
    /** How long the service handler takes, per command id (default 0). */
    private final Map<String, Long> serviceDelayMs = new ConcurrentHashMap<>();

    private HikariDataSource dsA;
    private HikariDataSource dsB;
    private Server serverA;
    private Server serverB;
    private JdbcTemplate jdbc;

    private record Server(String name, BusLifecycle lifecycle, ClusterRegistry cluster, CommandBrokerHandler broker,
                          int port, ExecutorService executor) {
    }

    @BeforeAll
    void setup() throws Exception {
        PostgresItSupport.createDatabase(POSTGRES, "idem_it");
        String url = PostgresItSupport.jdbcUrl(POSTGRES, "idem_it");
        dsA = PostgresItSupport.dataSource(POSTGRES, url, 10);
        dsB = PostgresItSupport.dataSource(POSTGRES, url, 10);
        PostgresItSupport.initializer(dsA, true).initializeDatabase();
        jdbc = new JdbcTemplate(dsA);

        serverA = startServer("server-a", dsA);
        serverB = startServer("server-b", dsB);
        for (var server : List.of(serverA, serverB)) {
            startAggregateBundle(server);
            startServiceBundle(server);
        }
    }

    @AfterAll
    void teardown() {
        for (var c : closeables.reversed()) {
            try { c.close(); } catch (Exception ignored) { /* best effort */ }
        }
        for (var s : List.of(serverA, serverB)) {
            if (s == null) continue;
            s.lifecycle().stop(Duration.ofMillis(500));
            s.executor().shutdownNow();
        }
        if (dsA != null) dsA.close();
        if (dsB != null) dsB.close();
    }

    @BeforeEach
    void reset() {
        aggregateCalls.set(0);
        serviceCalls.set(0);
        serviceDelayMs.clear();
    }

    // ------------------------------------------------------------------------------------ tests

    @Test
    void aRetryWithTheSameKeyOnTheSameAndOnAnotherServerIsAnsweredWithTheStoredEvent() throws Exception {
        var gatewayA = gateway(serverA);
        var gatewayB = gateway(serverB);
        long replaysBefore = replays();

        var orderId = "order-" + UUID.randomUUID();
        var key = "create-" + orderId;
        IdemEvt first = gatewayA.<IdemEvt>sendIdempotent(new IdemCmd(orderId), key).get(10, TimeUnit.SECONDS);
        IdemEvt sameServer = gatewayA.<IdemEvt>sendIdempotent(new IdemCmd(orderId), key).get(10, TimeUnit.SECONDS);
        IdemEvt otherServer = gatewayB.<IdemEvt>sendIdempotent(new IdemCmd(orderId), key).get(10, TimeUnit.SECONDS);

        assertThat(sameServer.nonce).as("same server: the stored event").isEqualTo(first.nonce);
        assertThat(otherServer.nonce).as("other server: the stored event").isEqualTo(first.nonce);
        assertThat(aggregateCalls).as("the aggregate handler ran once").hasValue(1);
        assertThat(rowsWithKey(key)).isEqualTo(1);
        assertThat(eventsOf(orderId)).isEqualTo(1);
        assertThat(replays() - replaysBefore).isEqualTo(2);

        // Same for a service command, whose handler has side effects.
        var payment = "pay-" + UUID.randomUUID();
        var payKey = "payment-" + payment;
        IdemServiceEvt paid = gatewayB.<IdemServiceEvt>sendIdempotent(new IdemServiceCmd(payment), payKey)
                .get(10, TimeUnit.SECONDS);
        IdemServiceEvt paidAgain = gatewayA.<IdemServiceEvt>sendIdempotent(new IdemServiceCmd(payment), payKey)
                .get(10, TimeUnit.SECONDS);
        assertThat(paidAgain.nonce).isEqualTo(paid.nonce);
        assertThat(serviceCalls).as("the payment ran once").hasValue(1);
        assertThat(rowsWithKey(payKey)).isEqualTo(1);
    }

    @Test
    void aRetryAfterTheCallerTimedOutWhileTheCommandCommittedDoesNotRunItAgain() throws Exception {
        // The ambiguous window of N14/S14: the first attempt fails for the caller (here its own
        // timeout; in the lab a BrokerChangedException) but the handler ran and the event is stored.
        // As in CommandDeadlineIT: the keyed payment queues ~2 s behind another payment of the
        // same account, is forwarded within its 3.5 s budget, and its handler answers ~3 s later.
        // The broker stores the event after the deadline (service_event_stored_after_deadline).
        var account = "acct-" + UUID.randomUUID();
        var blocker = new IdemServiceCmd("pay-blocker-" + UUID.randomUUID(), account);
        serviceDelayMs.put(blocker.id, 2_000L);
        var payment = new IdemServiceCmd("pay-slow-" + UUID.randomUUID(), account);
        var key = "payment-" + payment.id;
        serviceDelayMs.put(payment.id, 3_000L);

        var blocking = gateway(serverA).send(blocker, 10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> serviceCalls.get() == 1);
        var first = gateway(serverA).<IdemServiceEvt>sendIdempotent(payment, key, null, 3_500, TimeUnit.MILLISECONDS);
        assertThatThrownBy(() -> first.get(15, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .satisfies(e -> assertThat(e.getCause()).isInstanceOf(RequestTimeoutException.class));
        blocking.get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(10)).until(() -> rowsWithKey(key) == 1);

        IdemServiceEvt retried = gateway(serverB).<IdemServiceEvt>sendIdempotent(
                new IdemServiceCmd(payment.id, account), key).get(10, TimeUnit.SECONDS);
        assertThat(retried.id).isEqualTo(payment.id);
        assertThat(serviceCalls).as("blocker + the payment once: the retry replayed").hasValue(2);
        assertThat(rowsWithKey(key)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicatesOnTwoServersRunTheServiceHandlerOnce() throws Exception {
        var payment = "pay-race-" + UUID.randomUUID();
        var key = "payment-" + payment;
        serviceDelayMs.put(payment, 400L);
        var answers = race(8, i -> (i % 2 == 0 ? gateway(serverA) : gateway(serverB))
                .<IdemServiceEvt>sendIdempotent(new IdemServiceCmd(payment), key));

        assertThat(answers.stream().map(e -> ((IdemServiceEvt) e).nonce).distinct())
                .as("every duplicate got the one stored event").hasSize(1);
        assertThat(serviceCalls).as("one handler execution for 8 in-flight duplicates").hasValue(1);
        assertThat(rowsWithKey(key)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicatesOnTwoServersStoreOneAggregateEvent() throws Exception {
        var orderId = "order-race-" + UUID.randomUUID();
        var key = "create-" + orderId;
        var answers = race(8, i -> (i % 2 == 0 ? gateway(serverA) : gateway(serverB))
                .<IdemEvt>sendIdempotent(new IdemCmd(orderId), key));

        // The aggregate refuses a second creation; without the check under the aggregate lock a
        // duplicate would have been answered with that refusal instead of the stored event.
        assertThat(answers.stream().map(e -> ((IdemEvt) e).nonce).distinct()).hasSize(1);
        assertThat(rowsWithKey(key)).isEqualTo(1);
        assertThat(eventsOf(orderId)).isEqualTo(1);
    }

    @Test
    void withoutAnAggregateLockTheUniqueIndexStillStoresOneEvent() throws Exception {
        // A command that opts out of the aggregate lock: duplicates run the (pure) handler side by
        // side, and the partial unique index lets exactly one append through; the others replay it,
        // whether they lost the append or were refused by the domain on the grown story.
        var orderId = "order-lockless-" + UUID.randomUUID();
        var key = "create-" + orderId;
        var answers = race(8, i -> {
            var cmd = new IdemCmd(orderId);
            cmd.lockless = true;
            return (i % 2 == 0 ? gateway(serverA) : gateway(serverB)).<IdemEvt>sendIdempotent(cmd, key);
        });
        assertThat(answers.stream().map(e -> ((IdemEvt) e).nonce).distinct()).hasSize(1);
        assertThat(rowsWithKey(key)).isEqualTo(1);
        assertThat(eventsOf(orderId)).isEqualTo(1);
    }

    @Test
    void aConsumerRetryReissuesTheSameDerivedKeysAndRunsNothingTwice() throws Exception {
        // A saga handler that pays and then ships, run for event 42 of its consumer. The first
        // delivery goes through server A; the event is redelivered (the consumer failed before its
        // checkpoint) and the handler runs again, this time through server B.
        var consumerId = "saga-bundle_PaymentSaga_1_EUROPA";
        var order = "order-saga-" + UUID.randomUUID();
        var event = new DomainEventMessage(new IdemEvt(order, "trigger"));
        var first = sagaHandler(consumerGateway(serverA, consumerId, 42, event), order);
        var redelivered = sagaHandler(consumerGateway(serverB, consumerId, 42, event), order);

        assertThat(redelivered).isEqualTo(first);
        assertThat(serviceCalls).as("the payment ran once").hasValue(1);
        assertThat(aggregateCalls).as("the shipment ran once").hasValue(1);
        assertThat(rowsWithKey("evento:" + consumerId + ":42:0")).isEqualTo(1);
        assertThat(rowsWithKey("evento:" + consumerId + ":42:1")).isEqualTo(1);

        // A different event is a different intent: new keys, the handlers run.
        var next = sagaHandler(consumerGateway(serverA, consumerId, 43, event), order + "-2");
        assertThat(next).isNotEqualTo(first);
        assertThat(serviceCalls).hasValue(2);
    }

    @Test
    void aCommandWithoutAKeyBehavesAsBefore() throws Exception {
        // An old peer sends no key (or an unscoped gateway): each send is executed and stored.
        var gatewayA = gateway(serverA);
        var payment = "pay-legacy-" + UUID.randomUUID();
        IdemServiceEvt one = gatewayA.<IdemServiceEvt>send(new IdemServiceCmd(payment)).get(10, TimeUnit.SECONDS);
        IdemServiceEvt two = gateway(serverB).<IdemServiceEvt>send(new IdemServiceCmd(payment)).get(10, TimeUnit.SECONDS);
        assertThat(two.nonce).isNotEqualTo(one.nonce);
        assertThat(serviceCalls).hasValue(2);
        assertThat(jdbc.queryForObject("select count(*) from es__events where event_message like ? "
                + "and idempotency_key is null", Integer.class, "%" + payment + "%")).isEqualTo(2);
    }

    @Test
    void aCommandRefusedByItsHandlerIsNotRecordedAndCanBeRetriedWithTheSameKey() throws Exception {
        var orderId = "order-refused-" + UUID.randomUUID();
        var key = "create-" + orderId;
        var refused = new IdemCmd(orderId);
        refused.refuse = true;
        assertThatThrownBy(() -> gateway(serverA).sendIdempotent(refused, key).get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("refused");
        assertThat(rowsWithKey(key)).isZero();

        IdemEvt created = gateway(serverB).<IdemEvt>sendIdempotent(new IdemCmd(orderId), key).get(10, TimeUnit.SECONDS);
        assertThat(created.id).isEqualTo(orderId);
        assertThat(rowsWithKey(key)).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------ helpers

    /** Pays for {@code order}, then ships it: two commands, in this order. Returns both nonces. */
    private static List<String> sagaHandler(CommandGateway gateway, String order) throws Exception {
        IdemServiceEvt paid = gateway.<IdemServiceEvt>send(new IdemServiceCmd(order)).get(10, TimeUnit.SECONDS);
        IdemEvt shipped = gateway.<IdemEvt>send(new IdemCmd(order + "-shipment")).get(10, TimeUnit.SECONDS);
        return List.of(paid.nonce, shipped.nonce);
    }

    private GatewayTelemetryProxy consumerGateway(Server server, String consumerId, long seq,
                                                  DomainEventMessage handled) throws Exception {
        return new GatewayTelemetryProxy(gateway(server), null, "saga-bundle", null, "PaymentSaga", handled,
                new TracingAgent("saga-bundle", 1), "saga-1")
                .withDerivedIdempotencyKeys(consumerId, seq);
    }

    @FunctionalInterface
    private interface Send {
        CompletableFuture<?> send(int i) throws Exception;
    }

    private List<Object> race(int n, Send send) throws Exception {
        var go = new CountDownLatch(1);
        var futures = new ArrayList<CompletableFuture<Object>>();
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                int idx = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        go.await();
                        return send.send(idx).get(20, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }, exec));
            }
            go.countDown();
            var out = new ArrayList<Object>();
            for (var f : futures) out.add(f.get(30, TimeUnit.SECONDS));
            return out;
        }
    }

    private final Map<Server, CommandGateway> gateways = new ConcurrentHashMap<>();

    private CommandGateway gateway(Server server) {
        return gateways.computeIfAbsent(server, s -> {
            try {
                var client = BundleClient.builder("idem-caller", "caller-" + s.name() + "-" + UUID.randomUUID())
                        .host("127.0.0.1").port(s.port()).bundleVersion("1")
                        .handlerPayloadTypes(List.of())
                        .transportConfig(NettyTransportConfig.defaults())
                        .build();
                closeables.add(client::close);
                client.start().get(10, TimeUnit.SECONDS);
                return new CommandGatewayImpl(new EventoServerAdapter(client, "idem-caller", client.toString(), 1L));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private int rowsWithKey(String key) {
        return jdbc.queryForObject("select count(*) from es__events where idempotency_key = ?", Integer.class, key);
    }

    private int eventsOf(String aggregateId) {
        return jdbc.queryForObject("select count(*) from es__events where aggregate_id = ?", Integer.class, aggregateId);
    }

    private long replays() {
        return serverA.broker().idempotentReplayedCount() + serverB.broker().idempotentReplayedCount();
    }

    private Server startServer(String name, HikariDataSource ds) throws Exception {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var config = new NettyTransportConfig(
                Duration.ofSeconds(15), Duration.ofSeconds(45), Duration.ofSeconds(5),
                HandshakeProtocol.MAX_FRAME_LENGTH, 64 * 1024, 32 * 1024,
                new ExponentialBackoffWithJitter(), new JacksonCborCodec(), executor);
        var lifecycle = new BusLifecycle(new NettyServerTransport(config),
                connections, cluster, new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, name,
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), new JacksonCborPayloadCodec(),
                TokenValidator.acceptAll(),
                new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        int port = lifecycle.start(0);
        var store = new EventStore(null, null, new JdbcTemplate(ds), ds,
                1024, 1024, 150_000, 150_000, 69, EventStoreMode.CPES);
        var lock = new PgDistributedLock(ds);
        var broker = new CommandBrokerHandler(lifecycle, cluster, store, lock);
        return new Server(name, lifecycle, cluster, broker, port, executor);
    }

    /** An aggregate that creates once: a second creation of the same id is refused by the domain. */
    private void startAggregateBundle(Server server) throws Exception {
        var handler = new RegisteredHandler(ComponentType.Aggregate, "IdemAggregate",
                HandlerType.AggregateCommandHandler, PayloadType.DomainCommand, AGG_COMMAND,
                payloadName(IdemEvt.class), false, "id");
        var bundle = BundleClient.builder("idem-aggregate", "aggregate-" + server.name())
                .host("127.0.0.1").port(server.port()).bundleVersion("1")
                .registeredHandlers(List.of(handler))
                .handlerPayloadTypes(List.of(AGG_COMMAND))
                .transportConfig(NettyTransportConfig.defaults())
                .build();
        bundle.registerRequestHandler(AGG_COMMAND, (payload, ctx) -> {
            aggregateCalls.incrementAndGet();
            var request = codec.decodeRequest(payload);
            var decorated = (DecoratedDomainCommandMessage) request.getBody();
            var cmd = (IdemCmd) decorated.getCommandMessage().getPayload();
            var reply = new EventoResponse();
            reply.setCorrelationId(request.getCorrelationId());
            if (cmd.refuse || !decorated.getEventStream().isEmpty()) {
                reply.setBody(new ExceptionWrapper(new IllegalStateException(
                        cmd.refuse ? "refused by the domain" : "order " + cmd.id + " already created")));
            } else {
                reply.setBody(new DomainCommandResponseMessage(
                        new DomainEventMessage(new IdemEvt(cmd.id, UUID.randomUUID().toString())), null, false));
            }
            return codec.encodeResponse(reply);
        });
        closeables.add(bundle::close);
        bundle.start().get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> server.cluster().availableNodesFor(AGG_COMMAND).size() == 1);
    }

    /** A payment provider: every execution is a side effect, counted. */
    private void startServiceBundle(Server server) throws Exception {
        var handler = new RegisteredHandler(ComponentType.Service, "IdemService",
                HandlerType.CommandHandler, PayloadType.ServiceCommand, SERVICE_COMMAND,
                payloadName(IdemServiceEvt.class), false, null);
        var bundle = BundleClient.builder("idem-service", "service-" + server.name())
                .host("127.0.0.1").port(server.port()).bundleVersion("1")
                .registeredHandlers(List.of(handler))
                .handlerPayloadTypes(List.of(SERVICE_COMMAND))
                .transportConfig(NettyTransportConfig.defaults())
                .build();
        bundle.registerRequestHandler(SERVICE_COMMAND, (payload, ctx) -> {
            serviceCalls.incrementAndGet();
            var request = codec.decodeRequest(payload);
            var cmd = (IdemServiceCmd) ((ServiceCommandMessage) request.getBody()).getPayload();
            Thread.sleep(serviceDelayMs.getOrDefault(cmd.id, 0L));
            var reply = new EventoResponse();
            reply.setCorrelationId(request.getCorrelationId());
            reply.setBody(new ServiceEventMessage(new IdemServiceEvt(cmd.id, UUID.randomUUID().toString())));
            return codec.encodeResponse(reply);
        });
        closeables.add(bundle::close);
        bundle.start().get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> server.cluster().availableNodesFor(SERVICE_COMMAND).size() == 1);
    }

    private static String payloadName(Class<?> type) {
        return type.getName().substring(type.getName().lastIndexOf('.') + 1);
    }

    // ------------------------------------------------------------------------------------ payloads

    public static class IdemCmd extends DomainCommand {
        public String id;
        public boolean refuse;
        /** No broker lock at all (a custom {@code getLockId} returning null). */
        public boolean lockless;

        public IdemCmd() {
        }

        IdemCmd(String id) {
            this.id = id;
            // The IT's event store has no snapshot repository: never look a snapshot up.
            setInvalidateAggregateSnapshot(true);
        }

        @Override
        public String getAggregateId() {
            return id;
        }

        @Override
        public String getLockId() {
            return lockless ? null : id;
        }
    }

    public static class IdemEvt extends DomainEvent implements Serializable {
        public String id;
        public String nonce;

        public IdemEvt() {
        }

        IdemEvt(String id, String nonce) {
            this.id = id;
            this.nonce = nonce;
        }
    }

    public static class IdemServiceCmd extends ServiceCommand {
        public String id;
        /** The account whose payments the broker serialises ({@code RESOURCE:} lock); may be null. */
        public String account;

        public IdemServiceCmd() {
        }

        IdemServiceCmd(String id) {
            this(id, null);
        }

        IdemServiceCmd(String id, String account) {
            this.id = id;
            this.account = account;
        }

        @Override
        public String getLockId() {
            return account;
        }
    }

    public static class IdemServiceEvt extends ServiceEvent {
        public String id;
        public String nonce;

        public IdemServiceEvt() {
        }

        IdemServiceEvt(String id, String nonce) {
            this.id = id;
            this.nonce = nonce;
        }
    }
}
