package com.evento.server.es;

import com.evento.application.client.BundleClient;
import com.evento.application.client.EventoServerAdapter;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.messaging.gateway.CommandGatewayImpl;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.common.modeling.bundle.types.PayloadType;
import com.evento.common.modeling.messaging.message.application.DecoratedDomainCommandMessage;
import com.evento.common.modeling.messaging.message.application.DomainCommandResponseMessage;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.EventMessage;
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
import com.evento.server.es.CommandPhaseMetrics.Phase;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.JacksonCborCodec;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The phase timers of an aggregate command over real TCP: a broker with a JVM-local lock and an
 * event store whose story fetch, append and snapshot save take known times, an aggregate bundle
 * whose handler takes a known time, and a caller. Each phase must be at least its injected cost,
 * the phases must add up to the total (the stopwatch laps are consecutive), and keyed and unkeyed
 * commands land on their own {@code keyed} tag.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class CommandPhaseMetricsIT {

    private static final String COMMAND = payloadName(PhaseCmd.class);
    private static final long STORY_MS = 15;
    private static final long HANDLER_MS = 25;
    private static final long APPEND_MS = 20;
    private static final long SNAPSHOT_MS = 10;

    private final AdminPayloadCodec codec = new AdminPayloadCodec();
    private final List<BundleClient> clients = new CopyOnWriteArrayList<>();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<CommandPhaseMetrics.Sample> samples = new CopyOnWriteArrayList<>();
    private ExecutorService executor;
    private BusLifecycle lifecycle;
    private ClusterRegistry cluster;
    private CommandBrokerHandler broker;
    private int port;

    @BeforeEach
    void startBroker() throws Exception {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        cluster = new ClusterRegistry(connections);
        var config = new NettyTransportConfig(
                Duration.ofSeconds(15), Duration.ofSeconds(45), Duration.ofSeconds(5),
                HandshakeProtocol.MAX_FRAME_LENGTH, 64 * 1024, 32 * 1024,
                new ExponentialBackoffWithJitter(), new JacksonCborCodec(), executor);
        lifecycle = new BusLifecycle(new NettyServerTransport(config),
                connections, cluster, new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, "broker-phases",
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG), new JacksonCborPayloadCodec(),
                TokenValidator.acceptAll(), new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        port = lifecycle.start(0);
        broker = new CommandBrokerHandler(lifecycle, cluster, new SlowStore(), null);
        broker.bindTo(registry);
        broker.phaseMetrics().addListener(samples::add);
        startAggregate();
    }

    @AfterEach
    void tearDown() {
        for (var c : clients) {
            try { c.close(); } catch (Exception ignored) { /* best effort */ }
        }
        if (lifecycle != null) lifecycle.stop(Duration.ofMillis(500));
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void everyPhaseIsTimedAndThePhasesAddUpToTheTotal() throws Exception {
        var adapter = caller();
        var gateway = new CommandGatewayImpl(adapter);
        int unkeyed = 5;
        int keyed = 3;
        for (int i = 0; i < unkeyed; i++) {
            gateway.send(new PhaseCmd("unkeyed-" + UUID.randomUUID(), false)).get(10, TimeUnit.SECONDS);
        }
        for (int i = 0; i < keyed; i++) {
            var id = "keyed-" + UUID.randomUUID();
            gateway.sendIdempotent(new PhaseCmd(id, true), "key-" + id).get(10, TimeUnit.SECONDS);
        }
        // The sample is recorded when the reply's write completes, which can trail the caller.
        await().atMost(Duration.ofSeconds(5)).until(() -> samples.size() == unkeyed + keyed);

        assertThat(count(Phase.TOTAL, false)).isEqualTo(unkeyed);
        assertThat(count(Phase.TOTAL, true)).isEqualTo(keyed);
        assertThat(count(Phase.SNAPSHOT, true)).as("only commands whose handler returned a snapshot").isEqualTo(keyed);
        assertThat(count(Phase.SNAPSHOT, false)).isZero();
        for (var phase : List.of(Phase.QUEUE, Phase.CODEC, Phase.LOCK, Phase.STORY, Phase.FORWARD, Phase.VERIFY,
                Phase.APPEND, Phase.UNLOCK, Phase.REPLY)) {
            assertThat(count(phase, false)).as(phase.tag()).isEqualTo(unkeyed);
        }

        for (var s : samples) {
            assertThat(s.nanos(Phase.STORY)).as("story").isGreaterThanOrEqualTo(ms(STORY_MS));
            assertThat(s.nanos(Phase.FORWARD)).as("forward includes the handler").isGreaterThanOrEqualTo(ms(HANDLER_MS));
            assertThat(s.nanos(Phase.APPEND)).as("append").isGreaterThanOrEqualTo(ms(APPEND_MS));
            if (s.keyed()) assertThat(s.nanos(Phase.SNAPSHOT)).isGreaterThanOrEqualTo(ms(SNAPSHOT_MS));
            long total = s.nanos(Phase.TOTAL);
            long other = s.otherNanos();
            assertThat(other).as("phases never exceed the total").isGreaterThanOrEqualTo(0);
            assertThat(other).as("the phases add up to the total (%s)", s).isLessThan(Math.max(ms(2), total / 20));
        }
        // The caller's own round trip covers the broker's whole path.
        var rtt = adapter.client().latencies().requestRtt().histogram(COMMAND).snapshot();
        assertThat(rtt.count()).isEqualTo(unkeyed + keyed);
        assertThat(rtt.p50Nanos()).isGreaterThanOrEqualTo(ms(STORY_MS + HANDLER_MS + APPEND_MS));

        var timer = registry.get(CommandPhaseMetrics.METRIC).tag("phase", "forward").tag("keyed", "false").timer();
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(unkeyed * HANDLER_MS);
    }

    private long count(Phase phase, boolean keyed) {
        var timer = registry.find(CommandPhaseMetrics.METRIC)
                .tag("phase", phase.tag()).tag("keyed", String.valueOf(keyed)).timer();
        return timer == null ? 0 : timer.count();
    }

    private static long ms(long millis) {
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }

    private void startAggregate() throws Exception {
        var handler = new RegisteredHandler(ComponentType.Aggregate, "PhaseAggregate",
                HandlerType.AggregateCommandHandler, PayloadType.DomainCommand, COMMAND,
                payloadName(PhaseEvt.class), false, "id");
        var aggregate = BundleClient.builder("phase-aggregate", "aggregate-1")
                .host("127.0.0.1").port(port).bundleVersion("1")
                .registeredHandlers(List.of(handler))
                .handlerPayloadTypes(List.of(COMMAND))
                .transportConfig(NettyTransportConfig.defaults())
                .build();
        aggregate.registerRequestHandler(COMMAND, (payload, ctx) -> {
            var request = codec.decodeRequest(payload);
            var cmd = (PhaseCmd) ((DecoratedDomainCommandMessage) request.getBody()).getCommandMessage().getPayload();
            Thread.sleep(HANDLER_MS);
            var reply = new EventoResponse();
            reply.setCorrelationId(request.getCorrelationId());
            reply.setBody(new DomainCommandResponseMessage(new DomainEventMessage(new PhaseEvt(cmd.id)),
                    cmd.snapshot ? new SerializedAggregateState<>(null) : null, false));
            return codec.encodeResponse(reply);
        });
        clients.add(aggregate);
        aggregate.start().get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(() -> cluster.availableNodesFor(COMMAND).size() == 1);
    }

    private EventoServerAdapter caller() throws Exception {
        var client = BundleClient.builder("phase-caller", "caller-" + UUID.randomUUID())
                .host("127.0.0.1").port(port).bundleVersion("1")
                .handlerPayloadTypes(List.of())
                .transportConfig(NettyTransportConfig.defaults())
                .build();
        clients.add(client);
        client.start().get(10, TimeUnit.SECONDS);
        return new EventoServerAdapter(client, "phase-caller", client.toString(), 1L);
    }

    private static String payloadName(Class<?> type) {
        return type.getName().substring(type.getName().lastIndexOf('.') + 1);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class PhaseCmd extends DomainCommand {
        public String id;
        /** The handler returns a snapshot with its event. */
        public boolean snapshot;

        public PhaseCmd() {
        }

        PhaseCmd(String id, boolean snapshot) {
            this.id = id;
            this.snapshot = snapshot;
        }

        @Override
        public String getAggregateId() {
            return id;
        }
    }

    public static class PhaseEvt extends DomainEvent {
        public String id;

        public PhaseEvt() {
        }

        PhaseEvt(String id) {
            this.id = id;
        }
    }

    /** Records keys (so keyed commands are enforced) and takes a known time per operation. */
    static final class SlowStore implements BrokerEventStore {
        private final AtomicLong seq = new AtomicLong();

        @Override
        public AggregateStory fetchAggregateStory(String aggregateId, boolean invalidateCaches,
                                                  boolean invalidateSnapshot) {
            sleep(STORY_MS);
            return new AggregateStory(new SerializedAggregateState<>(null), List.of());
        }

        @Override
        public long publishEvent(EventMessage<?> eventMessage, String aggregateId) {
            sleep(APPEND_MS);
            return seq.incrementAndGet();
        }

        @Override
        public void saveSnapshot(String aggregateId, Long seqNum, SerializedAggregateState<?> state) {
            sleep(SNAPSHOT_MS);
        }

        @Override
        public void deleteAggregate(String aggregateId) {
        }

        @Override
        public boolean idempotencySupported() {
            return true;
        }
    }
}
