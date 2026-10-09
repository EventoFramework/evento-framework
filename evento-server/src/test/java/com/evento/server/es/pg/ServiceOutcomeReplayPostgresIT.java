package com.evento.server.es.pg;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.client.BundleClient;
import com.evento.application.client.EventoServerAdapter;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.messaging.gateway.CommandGatewayImpl;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.state.SerializedAggregateState;
import com.evento.common.utils.PgDistributedLock;
import com.evento.consumer.state.store.jdbc.JdbcServiceCommandOutcomeStore;
import com.evento.consumer.state.store.jdbc.SqlDialect;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.correlation.ForwardingDedupCache;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.bus.security.TokenValidator;
import com.evento.server.config.DistributedLockConfiguration;
import com.evento.server.es.AggregateStory;
import com.evento.server.es.BrokerEventStore;
import com.evento.server.es.CommandBrokerHandler;
import com.evento.server.es.EventStore;
import com.evento.server.es.EventStoreMode;
import com.evento.server.es.pg.outcome.OutcomePaidEvent;
import com.evento.server.es.pg.outcome.OutcomePayCommand;
import com.evento.server.es.pg.outcome.OutcomePaymentService;
import com.evento.transport.HandshakeProtocol;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * The G5 residual window, closed by the bundle's outcome store, over real TCP and a real Postgres:
 * a service handler (a payment, its row and its outcome in one transaction) ran, and its event
 * was then not stored. Before, the broker's retry ran the handler again (two payments). Now the
 * retry reaches the bundle, which answers from its {@link JdbcServiceCommandOutcomeStore}
 * without running the handler, and the broker stores that answer.
 *
 * <ul>
 *   <li>The append fails (the event store refuses it): the claim is released at once, the retry
 *       claims the key again and is answered from the outcome store.</li>
 *   <li>The server dies between the handler's answer and the append: its claim stays with a
 *       lease nobody renews; the bundle fails over to the second server, and a retry there takes
 *       the expired claim over, forwards, and is answered from the outcome store.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
@Timeout(value = 180, unit = TimeUnit.SECONDS)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class ServiceOutcomeReplayPostgresIT {

    private static final String PAY = "OutcomePayCommand";

    private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private PostgreSQLContainer postgres;
    private HikariDataSource esA;
    private HikariDataSource esB;
    private HikariDataSource app;
    private JdbcTemplate esJdbc;
    private JdbcTemplate appJdbc;
    private Server serverA;
    private Server serverB;
    private EventoBundle bundle;

    private record Server(String name, BusLifecycle lifecycle, ClusterRegistry cluster, FaultyStore store,
                          int port, ExecutorService executor) {
    }

    @BeforeAll
    void setup() throws Exception {
        postgres = PostgresItSupport.newContainer();
        postgres.start();
        PostgresItSupport.createDatabase(postgres, "outcome_es");
        PostgresItSupport.createDatabase(postgres, "outcome_app");
        String esUrl = PostgresItSupport.jdbcUrl(postgres, "outcome_es");
        esA = PostgresItSupport.dataSource(postgres, esUrl, 10);
        esB = PostgresItSupport.dataSource(postgres, esUrl, 10);
        app = PostgresItSupport.dataSource(postgres, PostgresItSupport.jdbcUrl(postgres, "outcome_app"), 10);
        PostgresItSupport.initializer(esA, true).initializeDatabase();
        esJdbc = new JdbcTemplate(esA);
        appJdbc = new JdbcTemplate(app);
        // The service's own database: its side-effect table and the outcome table (the Flyway
        // migration of evento-consumer-state-store-jdbc, run as a script here).
        appJdbc.execute("CREATE TABLE it_payments (order_id varchar(200) PRIMARY KEY, idempotency_key varchar(255), "
                + "attempts int NOT NULL DEFAULT 1)");
        appJdbc.execute(new ClassPathResource("db/migration/postgres/v2/V2__service_command_outcome.sql")
                .getContentAsString(StandardCharsets.UTF_8));

        serverA = startServer("outcome-a", esA);
        serverB = startServer("outcome-b", esB);
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(OutcomePaymentService.class.getPackage())
                .setBundleId("outcome-payments")
                .setInstanceId("outcome-payments-1")
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", serverA.port()),
                        new ClusterNodeAddress("127.0.0.1", serverB.port())))
                .setFailbackEnabled(false)
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .setInjector(Map.<Class<?>, Object>of(javax.sql.DataSource.class, app)::get)
                .setServiceCommandOutcomeStore(new JdbcServiceCommandOutcomeStore(app, SqlDialect.POSTGRES))
                .start();
        await().atMost(Duration.ofSeconds(20)).until(() -> serverA.cluster().availableNodesFor(PAY).size() == 1);
    }

    @AfterAll
    void teardown() {
        if (bundle != null) {
            try { bundle.getEngineSupervisor().stop(Duration.ofSeconds(2)); } catch (Exception ignored) { }
            try { ((EventoServerAdapter) bundle.getEventoServer()).close(); } catch (Exception ignored) { }
        }
        for (var c : closeables.reversed()) {
            try { c.close(); } catch (Exception ignored) { /* best effort */ }
        }
        for (var s : new Server[]{serverA, serverB}) {
            if (s == null) continue;
            s.lifecycle().stop(Duration.ofMillis(500));
            s.executor().shutdownNow();
        }
        for (var ds : new HikariDataSource[]{esA, esB, app}) {
            if (ds != null) ds.close();
        }
        clientExecutor.shutdownNow();
        if (postgres != null) postgres.stop();
    }

    @BeforeEach
    void reset() {
        OutcomePaymentService.EXECUTIONS.set(0);
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void aRetryAfterAFailedAppendIsAnsweredFromTheOutcomeStoreNotRunAgain() throws Exception {
        var orderId = "order-append-" + UUID.randomUUID();
        var key = "pay-" + orderId;
        serverA.store().failAppendOf = key;

        assertThatThrownBy(() -> gateway(serverA).sendIdempotent(new OutcomePayCommand(orderId), key, null,
                10, TimeUnit.SECONDS).get(15, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        assertThat(OutcomePaymentService.EXECUTIONS).as("the payment ran").hasValue(1);
        assertThat(eventsWithKey(key)).as("its event was not stored").isZero();
        assertThat(outcomeOf(key)).as("its outcome was, with the payment row").isEqualTo(1);

        OutcomePaidEvent retried = gateway(serverA).<OutcomePaidEvent>sendIdempotent(new OutcomePayCommand(orderId), key,
                null, 10, TimeUnit.SECONDS).get(15, TimeUnit.SECONDS);

        assertThat(retried.getOrderId()).isEqualTo(orderId);
        assertThat(OutcomePaymentService.EXECUTIONS).as("the retry did not run the payment again").hasValue(1);
        assertThat(attempts(orderId)).isEqualTo(1);
        assertThat(eventsWithKey(key)).as("the replayed outcome is now the stored event").isEqualTo(1);
        assertThat(storedEvent(key)).contains(retried.getNonce());

        // And from now on the broker answers the key itself (its claim statement finds the event).
        OutcomePaidEvent again = gateway(serverA).<OutcomePaidEvent>sendIdempotent(new OutcomePayCommand(orderId), key,
                null, 10, TimeUnit.SECONDS).get(15, TimeUnit.SECONDS);
        assertThat(again.getNonce()).isEqualTo(retried.getNonce());
        assertThat(OutcomePaymentService.EXECUTIONS).hasValue(1);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void aRetryAfterTheServerDiedBetweenTheAnswerAndTheAppendTakesTheClaimOverAndIsAnsweredFromTheStore()
            throws Exception {
        var orderId = "order-death-" + UUID.randomUUID();
        var key = "pay-" + orderId;
        var store = serverA.store();
        store.dieAtAppendOf = key;
        var callerA = gateway(serverA);

        // A short budget, so the dead owner's claim lease (deadline + forward budget + 10 s) ends soon.
        var first = callerA.sendIdempotent(new OutcomePayCommand(orderId), key, null, 3, TimeUnit.SECONDS);
        assertThat(store.reachedAppend.await(15, TimeUnit.SECONDS)).as("the handler answered").isTrue();
        assertThat(OutcomePaymentService.EXECUTIONS).hasValue(1);

        // Server A dies before it appends: its sessions drop, the bundle fails over to B, and the
        // append never happens (its claim stays behind, nobody releases or renews it).
        serverA.lifecycle().stop(Duration.ofMillis(200));
        store.released.countDown();
        assertThatThrownBy(() -> first.get(15, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
        assertThat(eventsWithKey(key)).isZero();
        assertThat(esJdbc.queryForObject("select count(*) from es__idempotency_claims where idempotency_key = ? "
                + "and completed_at is null", Integer.class, key)).as("the dead owner's claim").isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).until(() -> serverB.cluster().availableNodesFor(PAY).size() == 1);

        // The retry on B waits out the lease, takes the claim over and forwards: the bundle answers
        // from its outcome store, B stores the answer.
        long started = System.nanoTime();
        OutcomePaidEvent retried = gateway(serverB).<OutcomePaidEvent>sendIdempotent(new OutcomePayCommand(orderId), key,
                null, 40, TimeUnit.SECONDS).get(45, TimeUnit.SECONDS);
        long tookMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(retried.getOrderId()).isEqualTo(orderId);
        assertThat(OutcomePaymentService.EXECUTIONS).as("one payment, across a server death and a retry").hasValue(1);
        assertThat(attempts(orderId)).isEqualTo(1);
        assertThat(eventsWithKey(key)).isEqualTo(1);
        assertThat(storedEvent(key)).contains(retried.getNonce());
        System.out.println("event=residual_window_retry tookMs=" + tookMs);
    }

    // ------------------------------------------------------------------------------------ helpers

    private int eventsWithKey(String key) {
        return esJdbc.queryForObject("select count(*) from es__events where idempotency_key = ?", Integer.class, key);
    }

    private String storedEvent(String key) {
        return esJdbc.queryForObject("select event_message from es__events where idempotency_key = ?",
                String.class, key);
    }

    private int attempts(String orderId) {
        return appJdbc.queryForObject("select attempts from it_payments where order_id = ?", Integer.class, orderId);
    }

    private int outcomeOf(String key) {
        return appJdbc.queryForObject("select count(*) from evento_v2_service_outcome where idempotency_key = ?",
                Integer.class, key);
    }

    private final Map<Server, CommandGateway> gateways = new java.util.concurrent.ConcurrentHashMap<>();

    private CommandGateway gateway(Server server) {
        return gateways.computeIfAbsent(server, s -> {
            try {
                var client = BundleClient.builder("outcome-caller", "caller-" + s.name() + "-" + UUID.randomUUID())
                        .host("127.0.0.1").port(s.port()).bundleVersion("1")
                        .handlerPayloadTypes(List.of())
                        .transportConfig(transportConfig(clientExecutor))
                        .build();
                closeables.add(client::close);
                client.start().get(10, TimeUnit.SECONDS);
                return new CommandGatewayImpl(new EventoServerAdapter(client, "outcome-caller", client.toString(), 1L));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static NettyTransportConfig transportConfig(ExecutorService executor) {
        return new NettyTransportConfig(
                Duration.ofSeconds(15), Duration.ofSeconds(45), Duration.ofSeconds(5),
                HandshakeProtocol.MAX_FRAME_LENGTH, 64 * 1024, 32 * 1024,
                new ExponentialBackoffWithJitter(), new JacksonCborCodec(), executor);
    }

    private Server startServer(String name, HikariDataSource ds) throws Exception {
        var executor = Executors.newCachedThreadPool();
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var lifecycle = new BusLifecycle(new NettyServerTransport(transportConfig(executor)),
                connections, cluster, new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, name,
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), new JacksonCborPayloadCodec(),
                TokenValidator.acceptAll(),
                new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        int port = lifecycle.start(0);
        var eventStore = new EventStore(null, null, new JdbcTemplate(ds), ds,
                1024, 1024, 150_000, 150_000, 69, EventStoreMode.CPES);
        var store = new FaultyStore(eventStore);
        var lockConfig = new DistributedLockConfiguration();
        var lockPool = lockConfig.eventoLockConnectionPool(ds, 4, 1000);
        closeables.add(lockPool);
        PgDistributedLock lock = lockConfig.eventoDistributedLock(lockPool, 20_000L);
        new CommandBrokerHandler(lifecycle, cluster, store, lock);
        return new Server(name, lifecycle, cluster, store, port, executor);
    }

    /**
     * The production store, with two injected faults on the keyed service append of one key: an
     * append that fails, and a server that dies at the append (it blocks, then throws, and never
     * releases its claim, as a dead process would not).
     */
    static final class FaultyStore implements BrokerEventStore {
        private final EventStore delegate;
        volatile String failAppendOf;
        volatile String dieAtAppendOf;
        final CountDownLatch reachedAppend = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);

        FaultyStore(EventStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public long publishEvent(EventMessage<?> eventMessage, String aggregateId, String idempotencyKey,
                                 String commandType, String claimOwner) {
            if (idempotencyKey != null && idempotencyKey.equals(failAppendOf)) {
                failAppendOf = null;
                throw new IllegalStateException("append failed (injected)");
            }
            if (idempotencyKey != null && idempotencyKey.equals(dieAtAppendOf)) {
                reachedAppend.countDown();
                try {
                    released.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("the server died before the append (injected)");
            }
            return delegate.publishEvent(eventMessage, aggregateId, idempotencyKey, commandType, claimOwner);
        }

        @Override
        public void releaseIdempotencyClaim(String idempotencyKey, String owner) {
            if (idempotencyKey.equals(dieAtAppendOf)) return; // a dead server releases nothing
            delegate.releaseIdempotencyClaim(idempotencyKey, owner);
        }

        @Override
        public AggregateStory fetchAggregateStory(String aggregateId, boolean invalidateCaches, boolean invalidateSnapshot) {
            return delegate.fetchAggregateStory(aggregateId, invalidateCaches, invalidateSnapshot);
        }

        @Override
        public KeyedAggregateStory fetchAggregateStory(String aggregateId, boolean invalidateCaches,
                                                       boolean invalidateSnapshot, String idempotencyKey) {
            return delegate.fetchAggregateStory(aggregateId, invalidateCaches, invalidateSnapshot, idempotencyKey);
        }

        @Override
        public long publishEvent(EventMessage<?> eventMessage, String aggregateId) {
            return delegate.publishEvent(eventMessage, aggregateId);
        }

        @Override
        public long publishEvent(EventMessage<?> eventMessage, String aggregateId, String idempotencyKey,
                                 String commandType) {
            return delegate.publishEvent(eventMessage, aggregateId, idempotencyKey, commandType);
        }

        @Override
        public void saveSnapshot(String aggregateId, Long seqNum, SerializedAggregateState<?> state) {
            delegate.saveSnapshot(aggregateId, seqNum, state);
        }

        @Override
        public void deleteAggregate(String aggregateId) {
            delegate.deleteAggregate(aggregateId);
        }

        @Override
        public boolean idempotencySupported() {
            return delegate.idempotencySupported();
        }

        @Override
        public Optional<IdempotencyRecord> findIdempotencyRecord(String idempotencyKey) {
            return delegate.findIdempotencyRecord(idempotencyKey);
        }

        @Override
        public ClaimAttempt claimOrFindIdempotencyKey(String idempotencyKey, String commandType, String aggregateId,
                                                      String owner, long leaseMs) {
            return delegate.claimOrFindIdempotencyKey(idempotencyKey, commandType, aggregateId, owner, leaseMs);
        }

        @Override
        public boolean claimIdempotencyKey(String idempotencyKey, String commandType, String aggregateId,
                                           String owner, long leaseMs) {
            return delegate.claimIdempotencyKey(idempotencyKey, commandType, aggregateId, owner, leaseMs);
        }

        @Override
        public boolean renewIdempotencyClaim(String idempotencyKey, String owner, long leaseMs) {
            return delegate.renewIdempotencyClaim(idempotencyKey, owner, leaseMs);
        }

        @Override
        public boolean completeIdempotencyClaim(String idempotencyKey, String owner) {
            return delegate.completeIdempotencyClaim(idempotencyKey, owner);
        }
    }
}
