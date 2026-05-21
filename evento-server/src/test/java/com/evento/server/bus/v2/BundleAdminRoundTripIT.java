package com.evento.server.bus.v2;

import com.evento.application.client.v2.BundleClient;
import com.evento.application.client.v2.admin.BundleAdminRequestHandler;
import com.evento.application.consumer.ConsumerHandle;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.messaging.consumer.DeadPublishedEvent;
import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerDeleteDeadEventRequestMessage;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerFetchStatusRequestMessage;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerFetchStatusResponseMessage;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerProcessDeadQueueRequestMessage;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerResponseMessage;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerSetEventRetryRequestMessage;
import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.v2.correlation.CorrelationStore;
import com.evento.server.bus.v2.event.BusEventBus;
import com.evento.server.bus.v2.lifecycle.BusLifecycle;
import com.evento.server.bus.v2.registry.ClusterRegistry;
import com.evento.server.bus.v2.registry.ConnectionRegistry;
import com.evento.server.bus.v2.router.ForwardingTable;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.JacksonCborCodec;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.protocol.ProtocolPayloadTypes;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end exercise of the PR3.2a slice: a real {@link BundleClient}
 * wired with {@link BundleAdminRequestHandler} responds to admin requests
 * issued by the server-side {@link BusLifecycleFacade} over real TCP.
 *
 * <p>Proves the full path the dashboard's {@code ConsumerService}
 * methods will take on the v2 wire: a CBOR-encoded {@link EventoRequest}
 * goes out under {@link ProtocolPayloadTypes#SERVER_ADMIN_REQUEST}, the
 * bundle decodes it, runs the matching admin operation, and the
 * {@link EventoResponse} flows back to the facade callback.
 */
class BundleAdminRoundTripIT {

    /**
     * Test {@link ConsumerHandle} that captures invocations and returns canned
     * responses.
     */
    static final class TestConsumer implements ConsumerHandle {
        final String consumerId;
        final AtomicInteger deadQueueRuns = new AtomicInteger();
        final AtomicInteger deletes = new AtomicInteger();
        final AtomicInteger retrySets = new AtomicInteger();
        final AtomicLong lastRetrySeq = new AtomicLong();
        final AtomicBoolean lastRetryFlag = new AtomicBoolean();
        final AtomicLong lastDeletedSeq = new AtomicLong();
        final ConsumerFetchStatusResponseMessage statusToReturn;

        TestConsumer(String consumerId, ConsumerFetchStatusResponseMessage statusToReturn) {
            this.consumerId = consumerId;
            this.statusToReturn = statusToReturn;
        }

        @Override public String getConsumerId() { return consumerId; }
        @Override public ConsumerFetchStatusResponseMessage toConsumerStatus() { return statusToReturn; }
        @Override public long getLastConsumedEvent() { return 0L; }
        @Override public java.util.Collection<DeadPublishedEvent> getDeadEventQueue() { return java.util.List.of(); }
        @Override public void consumeDeadEventQueue() { deadQueueRuns.incrementAndGet(); }
        @Override public void setDeadEventRetry(long seq, boolean retry) {
            retrySets.incrementAndGet();
            lastRetrySeq.set(seq);
            lastRetryFlag.set(retry);
        }
        @Override public void deleteDeadEvent(long seq) {
            deletes.incrementAndGet();
            lastDeletedSeq.set(seq);
        }
    }

    private NettyTransportConfig nettyConfig;
    private BusLifecycle lifecycle;
    private BusLifecycleFacade facade;
    private AdminPayloadCodec adminCodec;
    private int port;

    @BeforeEach
    void setUp() {
        nettyConfig = new NettyTransportConfig(
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(5),
                16 * 1024 * 1024, 64 * 1024, 32 * 1024,
                new ExponentialBackoffWithJitter(), new JacksonCborCodec(),
                Executors.newVirtualThreadPerTaskExecutor());

        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofMillis(100));
        var forwarding = new ForwardingTable();
        adminCodec = new AdminPayloadCodec();
        var server = new NettyServerTransport(nettyConfig);
        lifecycle = new BusLifecycle(server, connections, cluster, correlations, forwarding,
                eventBus, "admin-it-server",
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                new JacksonCborPayloadCodec());
        port = lifecycle.start(0);
        facade = new BusLifecycleFacade(lifecycle, adminCodec, Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(500));
    }

    private BundleClient startBundleWithAdmin(TestConsumer consumer) {
        var client = BundleClient.builder("bundle-A", "inst-A1")
                .host("127.0.0.1").port(port).bundleVersion("100")
                .transportConfig(nettyConfig)
                .handlerPayloadTypes(java.util.List.of(ProtocolPayloadTypes.SERVER_ADMIN_REQUEST))
                .build();
        client.registerRequestHandler(ProtocolPayloadTypes.SERVER_ADMIN_REQUEST,
                new BundleAdminRequestHandler(adminCodec, (id, type) ->
                        id.equals(consumer.getConsumerId())
                                ? Optional.of(consumer) : Optional.empty()));
        client.start().orTimeout(3, TimeUnit.SECONDS).join();
        await().atMost(3, TimeUnit.SECONDS).until(() -> facade.currentAvailableView().size() == 1);
        return client;
    }

    private NodeAddress target() {
        return facade.currentView().iterator().next();
    }

    private EventoRequest adminRequest(java.io.Serializable body) {
        var r = new EventoRequest();
        r.setCorrelationId(UUID.randomUUID().toString());
        r.setTimestamp(System.currentTimeMillis());
        r.setSourceBundleId("evento-server");
        r.setSourceInstanceId("admin-it-server");
        r.setBody(body);
        return r;
    }

    @Test
    void fetchStatusRequestRoutesToConsumerAndReturnsStatus() throws Exception {
        var status = new ConsumerFetchStatusResponseMessage();
        status.setLastEventSequenceNumber(42L);
        var consumer = new TestConsumer("c-1", status);

        try (var bundle = startBundleWithAdmin(consumer)) {
            var future = new CompletableFuture<EventoResponse>();
            facade.forward(target(),
                    adminRequest(new ConsumerFetchStatusRequestMessage("c-1", ComponentType.Projector)),
                    future::complete);

            var resp = future.get(3, TimeUnit.SECONDS);
            assertThat(resp.getBody()).isInstanceOf(ConsumerFetchStatusResponseMessage.class);
            assertThat(((ConsumerFetchStatusResponseMessage) resp.getBody()).getLastEventSequenceNumber()).isEqualTo(42L);
        }
    }

    @Test
    void setRetryRequestInvokesConsumerSetDeadEventRetry() throws Exception {
        var consumer = new TestConsumer("c-2", new ConsumerFetchStatusResponseMessage());

        try (var bundle = startBundleWithAdmin(consumer)) {
            var future = new CompletableFuture<EventoResponse>();
            facade.forward(target(),
                    adminRequest(new ConsumerSetEventRetryRequestMessage("c-2", ComponentType.Saga, 17L, true)),
                    future::complete);

            var resp = future.get(3, TimeUnit.SECONDS);
            assertThat(resp.getBody()).isInstanceOf(ConsumerResponseMessage.class);
            assertThat(((ConsumerResponseMessage) resp.getBody()).isSuccess()).isTrue();
            assertThat(consumer.retrySets.get()).isEqualTo(1);
            assertThat(consumer.lastRetrySeq.get()).isEqualTo(17L);
            assertThat(consumer.lastRetryFlag.get()).isTrue();
        }
    }

    @Test
    void processDeadQueueRequestInvokesConsumerConsumeDeadEventQueue() throws Exception {
        var consumer = new TestConsumer("c-3", new ConsumerFetchStatusResponseMessage());

        try (var bundle = startBundleWithAdmin(consumer)) {
            var future = new CompletableFuture<EventoResponse>();
            facade.forward(target(),
                    adminRequest(new ConsumerProcessDeadQueueRequestMessage("c-3", ComponentType.Observer)),
                    future::complete);

            var resp = future.get(3, TimeUnit.SECONDS);
            assertThat(((ConsumerResponseMessage) resp.getBody()).isSuccess()).isTrue();
            assertThat(consumer.deadQueueRuns.get()).isEqualTo(1);
        }
    }

    @Test
    void deleteDeadEventRequestInvokesConsumerDeleteDeadEvent() throws Exception {
        var consumer = new TestConsumer("c-4", new ConsumerFetchStatusResponseMessage());

        try (var bundle = startBundleWithAdmin(consumer)) {
            var future = new CompletableFuture<EventoResponse>();
            facade.forward(target(),
                    adminRequest(new ConsumerDeleteDeadEventRequestMessage("c-4", ComponentType.Projector, 99L)),
                    future::complete);

            var resp = future.get(3, TimeUnit.SECONDS);
            assertThat(((ConsumerResponseMessage) resp.getBody()).isSuccess()).isTrue();
            assertThat(consumer.deletes.get()).isEqualTo(1);
            assertThat(consumer.lastDeletedSeq.get()).isEqualTo(99L);
        }
    }

    @Test
    void requestForUnknownConsumerSurfacesExceptionWrapperToFacade() throws Exception {
        var consumer = new TestConsumer("c-known", new ConsumerFetchStatusResponseMessage());

        try (var bundle = startBundleWithAdmin(consumer)) {
            var future = new CompletableFuture<EventoResponse>();
            facade.forward(target(),
                    adminRequest(new ConsumerFetchStatusRequestMessage("nope", ComponentType.Projector)),
                    future::complete);

            var resp = future.get(3, TimeUnit.SECONDS);
            assertThat(resp.getBody()).isInstanceOf(ExceptionWrapper.class);
            assertThat(((ExceptionWrapper) resp.getBody()).getMessage()).contains("consumer not found");
        }
    }
}
