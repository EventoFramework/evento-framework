package com.evento.server.es;

import com.evento.application.client.BundleClient;
import com.evento.application.client.EventoServerAdapter;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.common.modeling.messaging.message.application.DomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.DomainCommandResponseMessage;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.common.modeling.messaging.payload.DomainCommand;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.modeling.state.SerializedAggregateState;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.correlation.ForwardingDedupCache;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.bus.security.TokenValidator;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.RequestExpiredException;
import com.evento.transport.RequestTimeoutException;
import com.evento.transport.codec.JacksonCborCodec;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * N3 over real TCP: a command whose caller has stopped waiting is not executed.
 *
 * <p>A broker running {@link CommandBrokerHandler} (JVM-local lock, a recording event
 * store), an aggregate bundle whose handler sleeps for as long as each command asks, and
 * a caller going through {@link EventoServerAdapter} with a real timeout. Before the fix
 * the broker went on after the caller's timeout: it waited out the aggregate lock, took
 * a fresh full timeout for the forward and appended the event, so a caller that saw
 * "timed out" (and a saga that retried) had in fact applied the command.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class CommandDeadlineIT {

    /** The payload name a command message reports: the class name after the last dot. */
    private static final String COMMAND = payloadName(DeadlineCmd.class);

    private BusLifecycle lifecycle;
    private ClusterRegistry cluster;
    private ExecutorService serverExecutor;
    private int port;
    private final RecordingEventStore store = new RecordingEventStore();
    private final AtomicInteger handlerCalls = new AtomicInteger();
    private final List<BundleClient> clients = new CopyOnWriteArrayList<>();
    private final AdminPayloadCodec codec = new AdminPayloadCodec();

    private void startBroker(ExecutorService executor) throws Exception {
        serverExecutor = executor;
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        cluster = new ClusterRegistry(connections);
        var config = new NettyTransportConfig(
                Duration.ofSeconds(15), Duration.ofSeconds(45), Duration.ofSeconds(5),
                HandshakeProtocol.MAX_FRAME_LENGTH, 64 * 1024, 32 * 1024,
                new ExponentialBackoffWithJitter(), new JacksonCborCodec(), executor);
        lifecycle = new BusLifecycle(new NettyServerTransport(config),
                connections, cluster, new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, "broker-deadline",
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), new JacksonCborPayloadCodec(),
                TokenValidator.acceptAll(),
                new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        port = lifecycle.start(0);
        new CommandBrokerHandler(lifecycle, cluster, store, null);
    }

    @AfterEach
    void tearDown() {
        for (var c : clients) {
            try { c.close(); } catch (Exception ignored) { /* best effort */ }
        }
        if (lifecycle != null) lifecycle.stop(Duration.ofMillis(500));
        if (serverExecutor != null) serverExecutor.shutdownNow();
    }

    /** The aggregate bundle: answers every command with one event, after the command's delay. */
    private void startAggregate() throws Exception {
        var handler = new RegisteredHandler(ComponentType.Aggregate, "DeadlineAggregate",
                HandlerType.AggregateCommandHandler, PayloadType.DomainCommand, COMMAND,
                payloadName(DeadlineEvt.class), false, "id");
        var aggregate = BundleClient.builder("deadline-aggregate", "aggregate-1")
                .host("127.0.0.1").port(port).bundleVersion("1")
                .registeredHandlers(List.of(handler))
                .handlerPayloadTypes(List.of(COMMAND))
                .transportConfig(NettyTransportConfig.defaults())
                .build();
        aggregate.registerRequestHandler(COMMAND, (payload, ctx) -> {
            handlerCalls.incrementAndGet();
            var request = codec.decodeRequest(payload);
            var decorated = (com.evento.common.modeling.messaging.message.application.DecoratedDomainCommandMessage)
                    request.getBody();
            var cmd = (DeadlineCmd) decorated.getCommandMessage().getPayload();
            Thread.sleep(cmd.handlerDelayMs);
            var reply = new EventoResponse();
            reply.setCorrelationId(request.getCorrelationId());
            reply.setBody(new DomainCommandResponseMessage(
                    new DomainEventMessage(new DeadlineEvt(cmd.id)), null, false));
            return codec.encodeResponse(reply);
        });
        clients.add(aggregate);
        aggregate.start().get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> cluster.availableNodesFor(COMMAND).size() == 1);
    }

    private EventoServerAdapter caller() throws Exception {
        var client = BundleClient.builder("deadline-caller", "caller-" + UUID.randomUUID())
                .host("127.0.0.1").port(port).bundleVersion("1")
                .handlerPayloadTypes(List.of())
                .transportConfig(NettyTransportConfig.defaults())
                .build();
        clients.add(client);
        client.start().get(10, TimeUnit.SECONDS);
        return new EventoServerAdapter(client, "deadline-caller", client.toString(), 1L);
    }

    private static java.util.concurrent.CompletableFuture<java.io.Serializable> send(EventoServerAdapter caller, DeadlineCmd cmd,
                                                                      long timeoutMs) throws Exception {
        return caller.<java.io.Serializable>request(new DomainCommandMessage(cmd), timeoutMs, TimeUnit.MILLISECONDS);
    }

    private void warmUp(EventoServerAdapter caller) throws Exception {
        // Proves the whole path works (and that the broker registered the command) before
        // any deadline is exercised.
        send(caller, new DeadlineCmd("warm-up-" + UUID.randomUUID(), 0), 5_000).get(10, TimeUnit.SECONDS);
        assertThat(store.appended).hasSize(1);
        store.appended.clear();
        handlerCalls.set(0);
    }

    @Test
    void aCommandWhoseHandlerFinishesAfterTheCallersDeadlineIsNotAppended() throws Exception {
        startBroker(Executors.newVirtualThreadPerTaskExecutor());
        startAggregate();
        var caller = caller();
        warmUp(caller);

        // B waits ~300 ms for A's aggregate lock, then its handler needs 400 ms: past B's
        // 600 ms budget. The forward used to get a fresh full timeout after the lock wait,
        // so B's event was stored ~100 ms after its caller had given up.
        var aggregateId = "slow-" + UUID.randomUUID();
        var a = send(caller, new DeadlineCmd(aggregateId, 300), 10_000);
        await().atMost(Duration.ofSeconds(5)).until(() -> handlerCalls.get() == 1);
        var b = send(caller, new DeadlineCmd(aggregateId, 400), 600);
        assertThatThrownBy(() -> b.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .satisfies(e -> assertThat(e.getCause()).isInstanceOfAny(
                        RequestTimeoutException.class, RequestExpiredException.class));
        a.get(10, TimeUnit.SECONDS);

        // B reached its handler (the forward went out in time) but the answer came too
        // late: the caller saw a failure (its own timeout, or the broker's expiry if that
        // came first) and the broker stored nothing for it.
        await().atMost(Duration.ofSeconds(5)).until(() ->
                lifecycle.expiredDroppedCount(BusLifecycle.EXPIRED_STAGE_BEFORE_APPEND) == 1);
        Thread.sleep(500);
        assertThat(handlerCalls).hasValue(2);
        assertThat(store.appended).as("only A, whose caller waited, was stored").hasSize(1);
    }

    @Test
    void aCommandQueuedBehindTheAggregateLockPastItsDeadlineIsNeverForwarded() throws Exception {
        startBroker(Executors.newVirtualThreadPerTaskExecutor());
        startAggregate();
        var caller = caller();
        warmUp(caller);

        var aggregateId = "contended-" + UUID.randomUUID();
        // The first command holds the aggregate lock for 1.5 s; the caller waits for it.
        var holder = send(caller, new DeadlineCmd(aggregateId, 1_500), 10_000);
        await().atMost(Duration.ofSeconds(5)).until(() -> handlerCalls.get() == 1);
        // The second one queues behind that lock with a 400 ms budget.
        var queued = send(caller, new DeadlineCmd(aggregateId, 0), 400);
        // Its own timeout or the broker's expiry, whichever reaches the caller first.
        assertThatThrownBy(() -> queued.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .satisfies(e -> assertThat(e.getCause()).isInstanceOfAny(
                        RequestTimeoutException.class, RequestExpiredException.class));

        holder.get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() ->
                lifecycle.expiredDroppedCount(BusLifecycle.EXPIRED_STAGE_LOCK_WAIT) == 1);
        assertThat(handlerCalls).as("the expired command never reached the aggregate").hasValue(1);
        assertThat(store.appended).as("only the command whose caller waited was stored").hasSize(1);
    }

    @Test
    void aSaturatedBrokerDropsRequestsThatExpiredInItsDispatchQueue() throws Exception {
        // Two dispatch threads (a command needs one to wait for its forward and one to read
        // the reply), both then occupied by slow broker-local requests: whatever arrives
        // meanwhile waits in the executor queue, past its caller's deadline.
        startBroker(Executors.newFixedThreadPool(2));
        startAggregate();
        var caller = caller();
        warmUp(caller);
        lifecycle.registerLocalHandler("Blocker", payload -> {
            Thread.sleep(1_500);
            return new byte[0];
        });
        var callerClient = clients.getLast();

        var blocked = List.of(
                callerClient.request("Blocker", new byte[0], Duration.ofSeconds(10)),
                callerClient.request("Blocker", new byte[0], Duration.ofSeconds(10)));
        Thread.sleep(200);
        var queued = send(caller, new DeadlineCmd("queued-" + UUID.randomUUID(), 0), 500);
        assertThatThrownBy(() -> queued.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .satisfies(e -> assertThat(e.getCause()).isInstanceOfAny(
                        RequestTimeoutException.class, RequestExpiredException.class));
        for (var b : blocked) b.get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(5)).until(() ->
                lifecycle.expiredDroppedCount(BusLifecycle.EXPIRED_STAGE_QUEUED) == 1);
        assertThat(handlerCalls).hasValue(0);
        assertThat(store.appended).isEmpty();
    }

    @Test
    void aRequestWithoutATimeoutKeepsThePreviousBehaviour() throws Exception {
        startBroker(Executors.newVirtualThreadPerTaskExecutor());
        startAggregate();
        var client = BundleClient.builder("deadline-legacy", "legacy-1")
                .host("127.0.0.1").port(port).bundleVersion("1")
                .handlerPayloadTypes(List.of())
                .transportConfig(NettyTransportConfig.defaults())
                .build();
        clients.add(client);
        client.start().get(10, TimeUnit.SECONDS);

        // timeoutMillis = 0 on the wire, as an old peer that sets none: no deadline, the
        // broker runs the command to completion however long it takes.
        var response = client.request(COMMAND, envelope(new DeadlineCmd("legacy-" + UUID.randomUUID(), 800)), null)
                .get(10, TimeUnit.SECONDS);
        assertThat(response.isError()).as("%s", response.error()).isFalse();
        assertThat(store.appended).hasSize(1);
        for (var stage : BusLifecycle.EXPIRED_STAGES) {
            assertThat(lifecycle.expiredDroppedCount(stage)).as(stage).isZero();
        }
    }

    private byte[] envelope(DeadlineCmd cmd) {
        var request = new EventoRequest();
        request.setSourceBundleId("deadline-raw");
        request.setSourceInstanceId("raw-1");
        request.setSourceBundleVersion(1);
        request.setCorrelationId(UUID.randomUUID().toString());
        request.setBody(new DomainCommandMessage(cmd));
        request.setTimestamp(System.currentTimeMillis());
        request.setTimeout(5_000);
        request.setUnit(TimeUnit.MILLISECONDS);
        return codec.encodeRequest(request);
    }

    // ---------------------------------------------------------------- fixtures

    private static String payloadName(Class<?> type) {
        return type.getName().substring(type.getName().lastIndexOf('.') + 1);
    }

    public static class DeadlineCmd extends DomainCommand {
        public String id;
        public long handlerDelayMs;

        public DeadlineCmd() {
        }

        DeadlineCmd(String id, long handlerDelayMs) {
            this.id = id;
            this.handlerDelayMs = handlerDelayMs;
        }

        @Override
        public String getAggregateId() {
            return id;
        }
    }

    public static class DeadlineEvt extends DomainEvent {
        public String id;

        public DeadlineEvt() {
        }

        DeadlineEvt(String id) {
            this.id = id;
        }
    }

    /** The broker's event store, reduced to recording what it is asked to append. */
    static final class RecordingEventStore implements BrokerEventStore {
        final List<EventMessage<?>> appended = new CopyOnWriteArrayList<>();
        private final AtomicLong seq = new AtomicLong();

        @Override
        public AggregateStory fetchAggregateStory(String aggregateId, boolean invalidateCaches,
                                                  boolean invalidateSnapshot) {
            return new AggregateStory(new SerializedAggregateState<>(null), List.of());
        }

        @Override
        public long publishEvent(EventMessage<?> eventMessage, String aggregateId) {
            appended.add(eventMessage);
            return seq.incrementAndGet();
        }

        @Override
        public void saveSnapshot(String aggregateId, Long seqNum, SerializedAggregateState<?> state) {
        }

        @Override
        public void deleteAggregate(String aggregateId) {
        }
    }
}
