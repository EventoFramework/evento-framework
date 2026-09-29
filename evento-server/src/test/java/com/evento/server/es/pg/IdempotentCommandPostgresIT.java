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
import com.evento.common.modeling.exceptions.IdempotencyKeyInProgressException;
import com.evento.common.modeling.exceptions.IdempotencyKeyMismatchException;
import com.evento.common.modeling.messaging.message.application.DecoratedDomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.DomainCommandResponseMessage;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.messaging.message.application.ServiceCommandMessage;
import com.evento.common.modeling.messaging.message.application.ServiceEventMessage;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.common.modeling.messaging.payload.DomainCommand;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.messaging.payload.ServiceCommand;
import com.evento.common.modeling.messaging.payload.ServiceEvent;
import com.evento.common.modeling.state.SerializedAggregateState;
import com.evento.common.utils.PgDistributedLock;
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
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.RequestExpiredException;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * Each server's lock is built by the production {@link DistributedLockConfiguration} on a
 * dedicated, bounded lock pool of {@value #LOCK_POOL_SIZE} connections (production default: 10),
 * so a command that needed two lock connections at once would starve here.
 *
 * <p>Every handler puts a fresh nonce in its event, so a response that carries the first
 * execution's nonce is the stored event replayed, and a different nonce is a second execution.
 * Handler invocations are counted across both servers. This class runs the CPES event store;
 * {@link IdempotentCommandApesPostgresIT} runs every test again on APES.
 *
 * <p>Servers, bundles and callers run on platform threads here. On JDK 25.0.1 a virtual thread's
 * timed park ({@code Thread.sleep(150)}, a timed {@code Semaphore.tryAcquire}) can oversleep until
 * an unrelated, much later timer fires (up to the 20 s command deadlines of this IT) when many
 * timed parks are cancelled early, which the default virtual-thread scheduler's delay queue
 * (new in 25; JDK 24 is not affected) mishandles; a standalone reproducer shows 150 ms sleeps
 * taking ~19 s on 25.0.1 and ~150 ms on 24. With virtual threads this IT then fails for reasons
 * unrelated to idempotency, randomly.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 180, unit = TimeUnit.SECONDS)
@EnabledIfEnvironmentVariable(named = "EVENTO_RUN_JDBC_IT", matches = "true",
        disabledReason = "Set EVENTO_RUN_JDBC_IT=true to run Testcontainers-based JDBC IT.")
class IdempotentCommandPostgresIT {

    static final int LOCK_POOL_SIZE = 2;

    private static final String AGG_COMMAND = payloadName(IdemCmd.class);
    private static final String SERVICE_COMMAND = payloadName(IdemServiceCmd.class);
    private static final String NESTING_COMMAND = payloadName(IdemNestingCmd.class);

    private final AdminPayloadCodec codec = new AdminPayloadCodec();
    private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();
    private final AtomicInteger aggregateCalls = new AtomicInteger();
    private final AtomicInteger serviceCalls = new AtomicInteger();
    /** How long a handler takes, per command id (default 0). */
    private final Map<String, Long> delayMs = new ConcurrentHashMap<>();
    /** Aggregate handlers of these ids wait here until all of them have been called. */
    private final Map<String, CountDownLatch> barriers = new ConcurrentHashMap<>();
    /** What the nesting handler saw from its nested sends, per outer id. */
    private final Map<String, List<String>> nestedOutcomes = new ConcurrentHashMap<>();

    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private PostgreSQLContainer postgres;
    private HikariDataSource dsA;
    private HikariDataSource dsB;
    private Server serverA;
    private Server serverB;
    private JdbcTemplate jdbc;

    private record Server(String name, BusLifecycle lifecycle, ClusterRegistry cluster, CommandBrokerHandler broker,
                          SwitchableStore store, int port, ExecutorService executor) {
    }

    /** The event store mode under test. */
    EventStoreMode mode() {
        return EventStoreMode.CPES;
    }

    @BeforeAll
    void setup() throws Exception {
        postgres = PostgresItSupport.newContainer();
        postgres.start();
        PostgresItSupport.createDatabase(postgres, "idem_it");
        String url = PostgresItSupport.jdbcUrl(postgres, "idem_it");
        dsA = PostgresItSupport.dataSource(postgres, url, 10);
        dsB = PostgresItSupport.dataSource(postgres, url, 10);
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
        clientExecutor.shutdownNow();
        if (postgres != null) postgres.stop();
    }

    @BeforeEach
    void reset() {
        aggregateCalls.set(0);
        serviceCalls.set(0);
        delayMs.clear();
        barriers.clear();
        nestedOutcomes.clear();
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
        assertThat(jdbc.queryForObject("select idempotency_command from es__events where idempotency_key = ?",
                String.class, key)).as("the identity recorded with the key").isEqualTo(AGG_COMMAND);

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
        assertThat(claimsOf(payKey)).as("the claim is gone once the event holds the key").isZero();
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
        delayMs.put(blocker.id, 2_000L);
        var payment = new IdemServiceCmd("pay-slow-" + UUID.randomUUID(), account);
        var key = "payment-" + payment.id;
        delayMs.put(payment.id, 3_000L);

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
        delayMs.put(payment, 400L);
        long waitedBefore = replayed(CommandBrokerHandler.REPLAY_AFTER_WAIT);
        var answers = race(8, i -> (i % 2 == 0 ? gateway(serverA) : gateway(serverB))
                .<IdemServiceEvt>sendIdempotent(new IdemServiceCmd(payment), key));

        assertThat(answers.stream().map(e -> ((IdemServiceEvt) e).nonce).distinct())
                .as("every duplicate got the one stored event").hasSize(1);
        assertThat(serviceCalls).as("one handler execution for 8 in-flight duplicates").hasValue(1);
        assertThat(rowsWithKey(key)).isEqualTo(1);
        assertThat(replayed(CommandBrokerHandler.REPLAY_AFTER_WAIT) - waitedBefore)
                .as("the duplicates waited on the claim, holding nothing, and were answered after it").isPositive();
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
    void withoutAnAggregateLockTheUniqueIndexIsTheFenceAndTheLosersReplay() throws Exception {
        // A command that opts out of the aggregate lock: 8 duplicates run the (pure) handler side by
        // side, all from the empty story (the handlers wait for each other), so all 8 reach the
        // keyed append: the partial unique index lets exactly one through and the ON CONFLICT path
        // turns the other 7 into replays (LOW-6: the append_conflict path provably runs).
        var orderId = "order-lockless-" + UUID.randomUUID();
        var key = "create-" + orderId;
        barriers.put(orderId, new CountDownLatch(8));
        long conflictsBefore = replayed(CommandBrokerHandler.REPLAY_APPEND_CONFLICT);
        var answers = race(8, i -> {
            var cmd = new IdemCmd(orderId);
            cmd.lockless = true;
            return (i % 2 == 0 ? gateway(serverA) : gateway(serverB)).<IdemEvt>sendIdempotent(cmd, key);
        });
        assertThat(answers.stream().map(e -> ((IdemEvt) e).nonce).distinct()).hasSize(1);
        assertThat(aggregateCalls).as("every duplicate ran the pure handler").hasValue(8);
        assertThat(rowsWithKey(key)).isEqualTo(1);
        assertThat(eventsOf(orderId)).isEqualTo(1);
        assertThat(replayed(CommandBrokerHandler.REPLAY_APPEND_CONFLICT) - conflictsBefore)
                .as("the 7 losing appends conflicted and replayed").isEqualTo(7);
    }

    @Test
    void theKeyedAppendConflictsInsteadOfStoringTwiceWhenCalledDirectly() {
        // Store level, for this mode's append statement (CPES single statement / APES insert).
        var store = serverA.store();
        var key = "direct-" + UUID.randomUUID();
        store.publishEvent(new DomainEventMessage(new IdemEvt("direct", "n1")), "direct-agg", key, AGG_COMMAND);
        assertThatThrownBy(() -> store.publishEvent(new DomainEventMessage(new IdemEvt("direct", "n2")),
                "direct-agg", key, AGG_COMMAND))
                .isInstanceOf(BrokerEventStore.IdempotencyKeyConflictException.class);
        assertThat(rowsWithKey(key)).isEqualTo(1);
        var record = serverB.store().findIdempotencyRecord(key).orElseThrow();
        assertThat(((IdemEvt) record.event().eventMessage().getPayload()).nonce).isEqualTo("n1");
        assertThat(record.commandType()).isEqualTo(AGG_COMMAND);
        assertThat(record.aggregateId()).isEqualTo("direct-agg");
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
        assertThat(rowsWithKey("evento:" + consumerId + ":42:" + IdemServiceCmd.class.getSimpleName() + ":0")).isEqualTo(1);
        assertThat(rowsWithKey("evento:" + consumerId + ":42:" + IdemCmd.class.getSimpleName() + ":0")).isEqualTo(1);

        // A different event is a different intent: new keys, the handlers run.
        var next = sagaHandler(consumerGateway(serverA, consumerId, 43, event), order + "-2");
        assertThat(next).isNotEqualTo(first);
        assertThat(serviceCalls).hasValue(2);
    }

    @Test
    void aShiftedDerivedKeyIsRefusedAsAMismatchNeverAnsweredWithAnotherCommandsEvent() throws Exception {
        // HIGH-1. Attempt 1 of a saga handler ships two orders, A then B. Attempt 2 (the event
        // redelivered) skips A, say because a query now says A is shipped: B takes ordinal 0 of
        // its type, which is A's key. Before, the broker answered B with A's event and ran
        // nothing: B was silently never shipped. Now the key's recorded identity (type + aggregate)
        // does not match B, and the broker refuses it without executing anything.
        var consumerId = "saga-bundle_ShippingSaga_1_EUROPA";
        var event = new DomainEventMessage(new IdemEvt("trigger", "trigger"));
        var a = "ship-a-" + UUID.randomUUID();
        var b = "ship-b-" + UUID.randomUUID();
        var attempt1 = consumerGateway(serverA, consumerId, 77, event);
        IdemEvt shippedA = attempt1.<IdemEvt>send(new IdemCmd(a)).get(10, TimeUnit.SECONDS);
        attempt1.<IdemEvt>send(new IdemCmd(b)).get(10, TimeUnit.SECONDS);
        assertThat(aggregateCalls).hasValue(2);
        long mismatchesBefore = mismatches();

        var attempt2 = consumerGateway(serverB, consumerId, 77, event);
        assertThatThrownBy(() -> attempt2.send(new IdemCmd(b)).get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .satisfies(e -> assertThat(e.getCause()).isInstanceOf(IdempotencyKeyMismatchException.class)
                        .hasMessageContaining("idempotency key reused by a different command"));
        assertThat(mismatches() - mismatchesBefore).isEqualTo(1);
        assertThat(aggregateCalls).as("nothing executed for the mismatch").hasValue(2);
        assertThat(eventsOf(b)).isEqualTo(1);
        assertThat(shippedA.id).isEqualTo(a);

        // A different command type presenting a stored key: refused too.
        var key = "evento:" + consumerId + ":77:" + IdemCmd.class.getSimpleName() + ":0";
        assertThatThrownBy(() -> gateway(serverA).send(new IdemServiceCmd("pay-" + UUID.randomUUID()), null, null,
                10, TimeUnit.SECONDS, key).get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IdempotencyKeyMismatchException.class);
        assertThat(serviceCalls).hasValue(0);
    }

    @Test
    void keyedServiceCommandsNeverStarveTheLockPoolNorDeadlockWithTheirResourceLock() throws Exception {
        // HIGH-2. Each server's lock pool has LOCK_POOL_SIZE (2) connections. Before, a keyed
        // service command held an IDEMPOTENCY:<key> lock connection for its whole handler run and,
        // with a lockId, then waited for a second one for RESOURCE:<lockId>: two such commands
        // per server took both connections and waited for each other (hold-and-wait) until their
        // deadlines, and aggregate commands starved behind them. Now the key is a claim row: every
        // command holds at most one lock connection, and the whole mix completes.
        int n = 12;
        var tag = UUID.randomUUID().toString();
        var futures = new ArrayList<CompletableFuture<?>>();
        var go = new CountDownLatch(1);
        try (var exec = Executors.newCachedThreadPool()) {
            for (int i = 0; i < n; i++) {
                int idx = i;
                var gw = i % 2 == 0 ? gateway(serverA) : gateway(serverB);
                var other = i % 2 == 0 ? gateway(serverB) : gateway(serverA);
                var locked = new IdemServiceCmd("pay-locked-" + tag + "-" + idx, "acct-" + tag + "-" + idx);
                var unlocked = new IdemServiceCmd("pay-free-" + tag + "-" + idx);
                delayMs.put(locked.id, 150L);
                delayMs.put(unlocked.id, 150L);
                futures.add(submit(exec, go, () -> gw.sendIdempotent(locked, "k-" + locked.id, null, 20, TimeUnit.SECONDS)));
                // a concurrent duplicate of the locked one, through the other server
                futures.add(submit(exec, go, () -> other.sendIdempotent(
                        new IdemServiceCmd(locked.id, locked.account), "k-" + locked.id, null, 20, TimeUnit.SECONDS)));
                futures.add(submit(exec, go, () -> gw.sendIdempotent(unlocked, "k-" + unlocked.id, null, 20, TimeUnit.SECONDS)));
                futures.add(submit(exec, go, () -> other.sendIdempotent(
                        new IdemCmd("order-" + tag + "-" + idx), "k-order-" + tag + "-" + idx, null, 20, TimeUnit.SECONDS)));
            }
            long started = System.nanoTime();
            go.countDown();
            for (var f : futures) f.get(30, TimeUnit.SECONDS);
            long tookMs = (System.nanoTime() - started) / 1_000_000;
            assertThat(tookMs).as("no command waited for its deadline").isLessThan(15_000);
        }
        assertThat(serviceCalls).as("each keyed service command ran once").hasValue(2 * n);
        assertThat(aggregateCalls).hasValue(n);
        assertThat(jdbc.queryForObject("select count(*) from es__events where idempotency_key like ?",
                Integer.class, "k-%" + tag + "%")).isEqualTo(3 * n);
    }

    @Test
    void aVoidServiceHandlerRecordsItsKeySoARetryIsAnsweredInsteadOfReRun() throws Exception {
        // MEDIUM-1: the handler returns nothing, so no event carries the key. Before, the key was
        // recorded nowhere and every retry re-ran the side effect.
        var mail = new IdemServiceCmd("mail-" + UUID.randomUUID());
        mail.voidResult = true;
        var key = "mail-" + mail.id;
        Object first = gateway(serverA).sendIdempotent(mail, key).get(10, TimeUnit.SECONDS);
        Object retry = gateway(serverB).sendIdempotent(mail, key).get(10, TimeUnit.SECONDS);
        assertThat(first).isNull();
        assertThat(retry).isNull();
        assertThat(serviceCalls).as("the mail was sent once").hasValue(1);
        assertThat(rowsWithKey(key)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from es__idempotency_claims "
                + "where idempotency_key = ? and completed_at is not null", Integer.class, key)).isEqualTo(1);

        // Unkeyed, the old behaviour: each send runs.
        gateway(serverA).send(mail).get(10, TimeUnit.SECONDS);
        assertThat(serviceCalls).hasValue(2);
    }

    @Test
    void aDuplicateThatStopsWaitingForAnInFlightExecutionIsToldInProgressNotExpired() throws Exception {
        // MEDIUM-2. The original payment takes 3 s; a duplicate with a 1 s deadline arrives while
        // it runs. It must not be answered "expired, not executed" (RequestExpiredException): the
        // command it duplicates commits a moment later. The broker answers it in progress.
        var payment = new IdemServiceCmd("pay-inflight-" + UUID.randomUUID());
        var key = "payment-" + payment.id;
        delayMs.put(payment.id, 3_000L);
        long inProgressBefore = inProgress();
        long lockWaitDropsBefore = drops(BusLifecycle.EXPIRED_STAGE_LOCK_WAIT);

        var original = gateway(serverA).<IdemServiceEvt>sendIdempotent(payment, key, null, 10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> serviceCalls.get() == 1);
        var duplicate = gateway(serverB).sendIdempotent(new IdemServiceCmd(payment.id), key, null, 1, TimeUnit.SECONDS);
        assertThatThrownBy(() -> duplicate.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .satisfies(e -> assertThat(e.getCause())
                        .isNotInstanceOf(RequestExpiredException.class)
                        .isInstanceOfAny(IdempotencyKeyInProgressException.class, RequestTimeoutException.class));
        await().atMost(Duration.ofSeconds(5)).until(() -> inProgress() - inProgressBefore == 1);
        assertThat(drops(BusLifecycle.EXPIRED_STAGE_LOCK_WAIT) - lockWaitDropsBefore)
                .as("not an expired drop").isZero();

        IdemServiceEvt stored = original.get(10, TimeUnit.SECONDS);
        IdemServiceEvt retried = gateway(serverB).<IdemServiceEvt>sendIdempotent(new IdemServiceCmd(payment.id), key)
                .get(10, TimeUnit.SECONDS);
        assertThat(retried.nonce).isEqualTo(stored.nonce);
        assertThat(serviceCalls).hasValue(1);

        // The aggregate path: the duplicate waits for the aggregate lock the original holds.
        var orderId = "order-inflight-" + UUID.randomUUID();
        var orderKey = "create-" + orderId;
        delayMs.put(orderId, 3_000L);
        var created = gateway(serverA).<IdemEvt>sendIdempotent(new IdemCmd(orderId), orderKey, null, 10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> aggregateCalls.get() == 1);
        var dupCreate = gateway(serverB).sendIdempotent(new IdemCmd(orderId), orderKey, null, 1, TimeUnit.SECONDS);
        assertThatThrownBy(() -> dupCreate.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .satisfies(e -> assertThat(e.getCause()).isNotInstanceOf(RequestExpiredException.class));
        await().atMost(Duration.ofSeconds(5)).until(() -> inProgress() - inProgressBefore == 2);
        IdemEvt createdEvt = created.get(10, TimeUnit.SECONDS);
        IdemEvt again = gateway(serverB).<IdemEvt>sendIdempotent(new IdemCmd(orderId), orderKey).get(10, TimeUnit.SECONDS);
        assertThat(again.nonce).isEqualTo(createdEvt.nonce);
        assertThat(aggregateCalls).hasValue(1);
    }

    @Test
    void aHandlerForwardingItsKeyToANestedCommandGetsAMismatchNotACorruptedResultOrASelfBlock() throws Exception {
        // MEDIUM-3. A service handler that passes the key it received to its own nested commands.
        // Before: a nested aggregate command stored its event under the outer key, the outer
        // append then conflicted and the outer caller got the nested command's event; a nested
        // service command waited on the outer's IDEMPOTENCY lock until its deadline. Now the
        // outer's claim records its identity, so both nested sends are refused at once, and the
        // documented pattern (key + ":" + step) works.
        var outer = new IdemNestingCmd("outer-" + UUID.randomUUID());
        var key = "checkout-" + outer.id;
        long started = System.nanoTime();
        IdemServiceEvt result = gateway(serverA).<IdemServiceEvt>sendIdempotent(outer, key, null, 20, TimeUnit.SECONDS)
                .get(20, TimeUnit.SECONDS);
        long tookMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(result.id).as("the outer caller got the outer command's own event").isEqualTo(outer.id);
        assertThat(nestedOutcomes.get(outer.id)).containsExactly(
                IdempotencyKeyMismatchException.class.getName(),
                IdempotencyKeyMismatchException.class.getName(),
                "ok");
        assertThat(tookMs).as("no nested send waited for its deadline").isLessThan(8_000);
        assertThat(jdbc.queryForObject("select idempotency_command from es__events where idempotency_key = ?",
                String.class, key)).isEqualTo(NESTING_COMMAND);
        assertThat(rowsWithKey(key + ":ship")).isEqualTo(1);
    }

    @Test
    void aStoreWithoutKeySupportCostsNoLookupAndNoClaim() throws Exception {
        // LOW-2: with support off, a keyed command runs exactly like an unkeyed one.
        var store = serverB.store();
        store.supported = false;
        try {
            int lookups = store.lookups.get();
            int claims = store.claims.get();
            var payment = new IdemServiceCmd("pay-unsupported-" + UUID.randomUUID());
            var key = "payment-" + payment.id;
            gateway(serverB).sendIdempotent(payment, key).get(10, TimeUnit.SECONDS);
            gateway(serverB).sendIdempotent(payment, key).get(10, TimeUnit.SECONDS);
            assertThat(serviceCalls).as("no exactly-once without support").hasValue(2);
            assertThat(store.lookups.get()).isEqualTo(lookups);
            assertThat(store.claims.get()).isEqualTo(claims);
            assertThat(rowsWithKey(key)).isZero();
        } finally {
            store.supported = true;
        }
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

        // The same for a service command: a refusal releases the claim.
        var payment = new IdemServiceCmd("pay-refused-" + UUID.randomUUID());
        payment.refuse = true;
        var payKey = "payment-" + payment.id;
        assertThatThrownBy(() -> gateway(serverA).sendIdempotent(payment, payKey).get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        assertThat(claimsOf(payKey)).isZero();
        IdemServiceEvt paid = gateway(serverB).<IdemServiceEvt>sendIdempotent(new IdemServiceCmd(payment.id), payKey)
                .get(10, TimeUnit.SECONDS);
        assertThat(paid.id).isEqualTo(payment.id);
    }

    // ------------------------------------------------------------------------------------ helpers

    /** Pays for {@code order}, then ships it: two commands, in this order. Returns both nonces. */
    private static List<String> sagaHandler(CommandGateway gateway, String order) throws Exception {
        IdemServiceEvt paid = gateway.<IdemServiceEvt>send(new IdemServiceCmd(order)).get(10, TimeUnit.SECONDS);
        IdemEvt shipped = gateway.<IdemEvt>send(new IdemCmd(order + "-shipment")).get(10, TimeUnit.SECONDS);
        return List.of(paid.nonce, shipped.nonce);
    }

    private GatewayTelemetryProxy consumerGateway(Server server, String consumerId, long seq,
                                                  DomainEventMessage handled) {
        return new GatewayTelemetryProxy(gateway(server), null, "saga-bundle", null, "PaymentSaga", handled,
                new TracingAgent("saga-bundle", 1), "saga-1")
                .withDerivedIdempotencyKeys(consumerId, seq);
    }

    @FunctionalInterface
    private interface Send {
        CompletableFuture<?> send(int i) throws Exception;
    }

    private static CompletableFuture<Object> submit(ExecutorService exec, CountDownLatch go,
                                                    java.util.concurrent.Callable<CompletableFuture<?>> send) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                go.await();
                return send.call().get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, exec);
    }

    private List<Object> race(int n, Send send) throws Exception {
        var go = new CountDownLatch(1);
        var futures = new ArrayList<CompletableFuture<Object>>();
        try (var exec = Executors.newCachedThreadPool()) {
            for (int i = 0; i < n; i++) {
                int idx = i;
                futures.add(submit(exec, go, () -> send.send(idx)));
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
                        .transportConfig(clientTransportConfig())
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

    private int claimsOf(String key) {
        return jdbc.queryForObject("select count(*) from es__idempotency_claims where idempotency_key = ?",
                Integer.class, key);
    }

    private int eventsOf(String aggregateId) {
        return jdbc.queryForObject("select count(*) from es__events where aggregate_id = ?", Integer.class, aggregateId);
    }

    private long replays() {
        return serverA.broker().idempotentReplayedCount() + serverB.broker().idempotentReplayedCount();
    }

    private long replayed(String stage) {
        return serverA.broker().idempotentReplayedCount(stage) + serverB.broker().idempotentReplayedCount(stage);
    }

    private long mismatches() {
        return serverA.broker().idempotencyMismatchCount() + serverB.broker().idempotencyMismatchCount();
    }

    private long inProgress() {
        return serverA.broker().idempotencyInProgressCount() + serverB.broker().idempotencyInProgressCount();
    }

    private long drops(String stage) {
        return serverA.lifecycle().expiredDroppedCount(stage) + serverB.lifecycle().expiredDroppedCount(stage);
    }

    private Server startServer(String name, HikariDataSource ds) throws Exception {
        var executor = Executors.newCachedThreadPool();
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
        var eventStore = new EventStore(null, null, new JdbcTemplate(ds), ds,
                1024, 1024, 150_000, 150_000, 69, mode());
        // Both "servers" run in this JVM, so their APES Snowflakes would derive the same node id
        // from the host's MAC and collide on event_sequence_number; real servers run on
        // different hosts. Give each its own node id.
        var snowflake = EventStore.class.getDeclaredField("snowflake");
        snowflake.setAccessible(true);
        snowflake.set(eventStore, new com.evento.common.utils.Snowflake(name.equals("server-a") ? 1 : 2));
        var store = new SwitchableStore(eventStore);
        // The production lock: a dedicated, bounded pool copied from the application pool.
        var lockConfig = new DistributedLockConfiguration();
        var lockPool = lockConfig.eventoLockConnectionPool(ds, LOCK_POOL_SIZE, 1000);
        closeables.add(lockPool);
        PgDistributedLock lock = lockConfig.eventoDistributedLock(lockPool, 20_000L);
        var broker = new CommandBrokerHandler(lifecycle, cluster, store, lock);
        return new Server(name, lifecycle, cluster, broker, store, port, executor);
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
                .transportConfig(clientTransportConfig())
                .build();
        bundle.registerRequestHandler(AGG_COMMAND, (payload, ctx) -> {
            aggregateCalls.incrementAndGet();
            var request = codec.decodeRequest(payload);
            var decorated = (DecoratedDomainCommandMessage) request.getBody();
            var cmd = (IdemCmd) decorated.getCommandMessage().getPayload();
            var barrier = barriers.get(cmd.id);
            if (barrier != null) {
                barrier.countDown();
                barrier.await(10, TimeUnit.SECONDS);
            }
            Thread.sleep(delayMs.getOrDefault(cmd.id, 0L));
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

    /**
     * A payment provider (every execution is a side effect, counted), and a checkout service
     * whose handler sends nested commands through the caller gateway of its server.
     */
    private void startServiceBundle(Server server) throws Exception {
        var payments = new RegisteredHandler(ComponentType.Service, "IdemService",
                HandlerType.CommandHandler, PayloadType.ServiceCommand, SERVICE_COMMAND,
                payloadName(IdemServiceEvt.class), false, null);
        var checkout = new RegisteredHandler(ComponentType.Service, "IdemCheckout",
                HandlerType.CommandHandler, PayloadType.ServiceCommand, NESTING_COMMAND,
                payloadName(IdemServiceEvt.class), false, null);
        var bundle = BundleClient.builder("idem-service", "service-" + server.name())
                .host("127.0.0.1").port(server.port()).bundleVersion("1")
                .registeredHandlers(List.of(payments, checkout))
                .handlerPayloadTypes(List.of(SERVICE_COMMAND, NESTING_COMMAND))
                .transportConfig(clientTransportConfig())
                .build();
        bundle.registerRequestHandler(SERVICE_COMMAND, (payload, ctx) -> {
            serviceCalls.incrementAndGet();
            var request = codec.decodeRequest(payload);
            var cmd = (IdemServiceCmd) ((ServiceCommandMessage) request.getBody()).getPayload();
            Thread.sleep(delayMs.getOrDefault(cmd.id, 0L));
            var reply = new EventoResponse();
            reply.setCorrelationId(request.getCorrelationId());
            if (cmd.refuse) {
                reply.setBody(new ExceptionWrapper(new IllegalStateException("payment refused")));
            } else if (cmd.voidResult) {
                reply.setBody(new ServiceEventMessage((ServiceEvent) null));
            } else {
                reply.setBody(new ServiceEventMessage(new IdemServiceEvt(cmd.id, UUID.randomUUID().toString())));
            }
            return codec.encodeResponse(reply);
        });
        bundle.registerRequestHandler(NESTING_COMMAND, (payload, ctx) -> {
            var request = codec.decodeRequest(payload);
            var message = (ServiceCommandMessage) request.getBody();
            var cmd = (IdemNestingCmd) message.getPayload();
            var key = message.getIdempotencyKey();
            var outcomes = new ArrayList<String>();
            var gw = gateway(server);
            // Wrong: forwarding the received key to nested commands.
            outcomes.add(outcome(gw.sendIdempotent(new IdemCmd(cmd.id + "-ship"), key, null, 10, TimeUnit.SECONDS)));
            outcomes.add(outcome(gw.sendIdempotent(new IdemServiceCmd(cmd.id + "-pay"), key, null, 10, TimeUnit.SECONDS)));
            // Right: a key of its own per step.
            outcomes.add(outcome(gw.sendIdempotent(new IdemCmd(cmd.id + "-ship"), key + ":ship", null, 10, TimeUnit.SECONDS)));
            nestedOutcomes.put(cmd.id, outcomes);
            var reply = new EventoResponse();
            reply.setCorrelationId(request.getCorrelationId());
            reply.setBody(new ServiceEventMessage(new IdemServiceEvt(cmd.id, UUID.randomUUID().toString())));
            return codec.encodeResponse(reply);
        });
        closeables.add(bundle::close);
        bundle.start().get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> server.cluster().availableNodesFor(SERVICE_COMMAND).size() == 1
                && server.cluster().availableNodesFor(NESTING_COMMAND).size() == 1);
    }

    /**
     * Transport config for the bundles and callers of this IT, on platform threads (see the class
     * javadoc: timed parks of virtual threads can oversleep by seconds on JDK 25.0.1).
     */
    private NettyTransportConfig clientTransportConfig() {
        return new NettyTransportConfig(
                Duration.ofSeconds(15), Duration.ofSeconds(45), Duration.ofSeconds(5),
                HandshakeProtocol.MAX_FRAME_LENGTH, 64 * 1024, 32 * 1024,
                new ExponentialBackoffWithJitter(), new JacksonCborCodec(), clientExecutor);
    }

    private static String outcome(CompletableFuture<?> future) {
        try {
            future.get(15, TimeUnit.SECONDS);
            return "ok";
        } catch (ExecutionException e) {
            return e.getCause().getClass().getName();
        } catch (Exception e) {
            return e.getClass().getName();
        }
    }

    private static String payloadName(Class<?> type) {
        return type.getName().substring(type.getName().lastIndexOf('.') + 1);
    }

    /** The production store, with support switchable off and lookups/claims counted (LOW-2). */
    static final class SwitchableStore implements BrokerEventStore {
        private final EventStore delegate;
        volatile boolean supported = true;
        final AtomicInteger lookups = new AtomicInteger();
        final AtomicInteger claims = new AtomicInteger();

        SwitchableStore(EventStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public AggregateStory fetchAggregateStory(String aggregateId, boolean invalidateCaches, boolean invalidateSnapshot) {
            return delegate.fetchAggregateStory(aggregateId, invalidateCaches, invalidateSnapshot);
        }

        @Override
        public long publishEvent(EventMessage<?> eventMessage, String aggregateId) {
            return delegate.publishEvent(eventMessage, aggregateId);
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
            return supported && delegate.idempotencySupported();
        }

        @Override
        public Optional<IdempotencyRecord> findIdempotencyRecord(String idempotencyKey) {
            lookups.incrementAndGet();
            return delegate.findIdempotencyRecord(idempotencyKey);
        }

        @Override
        public long publishEvent(EventMessage<?> eventMessage, String aggregateId, String idempotencyKey,
                                 String commandType) {
            return supported ? delegate.publishEvent(eventMessage, aggregateId, idempotencyKey, commandType)
                    : delegate.publishEvent(eventMessage, aggregateId);
        }

        @Override
        public boolean claimIdempotencyKey(String idempotencyKey, String commandType, String aggregateId,
                                           String owner, long leaseMs) {
            claims.incrementAndGet();
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

        @Override
        public void releaseIdempotencyClaim(String idempotencyKey, String owner) {
            delegate.releaseIdempotencyClaim(idempotencyKey, owner);
        }
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
        public boolean refuse;
        /** The handler returns nothing (a void service handler). */
        public boolean voidResult;

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

    /** A checkout whose handler sends nested commands. */
    public static class IdemNestingCmd extends ServiceCommand {
        public String id;

        public IdemNestingCmd() {
        }

        IdemNestingCmd(String id) {
            this.id = id;
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
