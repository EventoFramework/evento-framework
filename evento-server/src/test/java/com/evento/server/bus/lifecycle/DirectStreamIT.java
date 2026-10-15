package com.evento.server.bus.lifecycle;

import com.evento.application.client.BundleClient;
import com.evento.application.client.handler.HandlerRegistry;
import com.evento.application.client.stream.DirectStreamConfig;
import com.evento.application.client.stream.InboundStream;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.correlation.ForwardingDedupCache;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
import com.evento.server.bus.security.TokenValidator;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.message.Hello;
import com.evento.transport.message.Message;
import com.evento.transport.message.Reject;
import com.evento.transport.netty.NettyClientTransport;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Streams taken on a direct connection: the broker routes the query and relays one
 * redirect, the data flows between the two bundles, and a caller that cannot reach the
 * endpoint gets the same stream through the broker.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class DirectStreamIT {

    private static final String EXPORT = "ExportOrders";
    private static final int FRAMES = 300;

    private BusLifecycle lifecycle;
    private int port;

    @BeforeEach
    void setUp() {
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        lifecycle = new BusLifecycle(new NettyServerTransport(NettyTransportConfig.defaults()),
                connections, new ClusterRegistry(connections), new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, "broker-direct", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                new JacksonCborPayloadCodec(), TokenValidator.acceptAll(),
                new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        port = lifecycle.start(0);
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(500));
    }

    private BundleClient handlerBundle(DirectStreamConfig direct) {
        return BundleClient.builder("reports", "reports-1")
                .host("127.0.0.1").port(port)
                .handlerPayloadTypes(List.of(EXPORT))
                .directStreams(direct)
                .transportConfig(NettyTransportConfig.defaults())
                .build();
    }

    private BundleClient caller() {
        return BundleClient.builder("web", "web-1")
                .host("127.0.0.1").port(port)
                .handlerPayloadTypes(List.of())
                .transportConfig(NettyTransportConfig.defaults())
                .build();
    }

    private static final class Exporter implements HandlerRegistry.StreamingRequestHandler {
        final AtomicInteger sent = new AtomicInteger();
        final AtomicReference<Throwable> stoppedBy = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        final int frames;

        Exporter(int frames) { this.frames = frames; }

        @Override
        public byte[] handle(byte[] payload, HandlerRegistry.RequestContext context) {
            return new byte[0];
        }

        @Override
        public void handleStream(byte[] payload, HandlerRegistry.RequestContext context,
                                 HandlerRegistry.StreamSink sink) throws Exception {
            try {
                for (int i = 0; i < frames; i++) {
                    var frame = new byte[32 * 1024];
                    ByteBuffer.wrap(frame).putInt(i);
                    sink.send(frame);
                    sent.incrementAndGet();
                }
            } catch (Throwable t) {
                stoppedBy.set(t);
                throw t;
            } finally {
                done.countDown();
            }
        }
    }

    private static List<Integer> drain(InboundStream stream) throws Exception {
        var indices = new ArrayList<Integer>();
        while (true) {
            var item = stream.next();
            if (item instanceof InboundStream.Item.End) return indices;
            if (item instanceof InboundStream.Item.Frame(byte[] payload)) indices.add(ByteBuffer.wrap(payload).getInt());
        }
    }

    @Test
    void theDataFlowsBetweenTheBundlesAndTheBrokerRelaysOnlyTheRedirect() throws Exception {
        var exporter = new Exporter(FRAMES);
        try (var handler = handlerBundle(DirectStreamConfig.on(0, "127.0.0.1")); var web = caller()) {
            handler.registerRequestHandler(EXPORT, exporter);
            handler.start().get(5, TimeUnit.SECONDS);
            web.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);
            assertThat(handler.directStreamPort()).isPositive();
            long bytesBefore = lifecycle.streamBytesRelayed();

            List<Integer> received;
            try (var stream = web.openStream(EXPORT, new byte[0], Duration.ofSeconds(10), 8, true)) {
                received = drain(stream);
            }

            assertThat(received).hasSize(FRAMES);
            for (int i = 0; i < FRAMES; i++) assertThat(received.get(i)).isEqualTo(i);
            assertThat(handler.directStreamsServed()).isEqualTo(1);
            assertThat(web.directStreamsFollowed()).isEqualTo(1);
            assertThat(web.directStreamFallbacks()).isZero();
            // ~9.6 MB went bundle to bundle; the broker carried a redirect of a few hundred bytes.
            assertThat(lifecycle.streamBytesRelayed() - bytesBefore).isLessThan(4_096);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.activeStreams() == 0);
        }
    }

    @Test
    void anUnreachableEndpointFallsBackToTheBroker() throws Exception {
        int closedPort;
        try (var probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        }
        var exporter = new Exporter(50);
        var direct = DirectStreamConfig.on(0, "127.0.0.1").withAdvertisedPort(closedPort);
        try (var handler = handlerBundle(direct); var web = caller()) {
            handler.registerRequestHandler(EXPORT, exporter);
            handler.start().get(5, TimeUnit.SECONDS);
            web.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            List<Integer> received;
            try (var stream = web.openStream(EXPORT, new byte[0], Duration.ofSeconds(10), 8, true)) {
                received = drain(stream);
            }

            assertThat(received).hasSize(50);
            assertThat(web.directStreamFallbacks()).isEqualTo(1);
            assertThat(web.directStreamsFollowed()).isZero();
            assertThat(handler.directStreamsServed()).isZero();
            assertThat(lifecycle.streamFramesRelayed()).isGreaterThanOrEqualTo(50);
        }
    }

    @Test
    void closingADirectStreamEarlyStopsTheHandler() throws Exception {
        var exporter = new Exporter(100_000);
        try (var handler = handlerBundle(DirectStreamConfig.on(0, "127.0.0.1")); var web = caller()) {
            handler.registerRequestHandler(EXPORT, exporter);
            handler.start().get(5, TimeUnit.SECONDS);
            web.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            var stream = web.openStream(EXPORT, new byte[0], Duration.ofSeconds(10), 8, true);
            for (int i = 0; i < 5; i++) assertThat(stream.next()).isInstanceOf(InboundStream.Item.Frame.class);
            stream.close();

            assertThat(exporter.done.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(exporter.stoppedBy.get()).isInstanceOf(HandlerRegistry.StreamCancelledException.class);
            assertThat(exporter.sent.get()).isLessThan(100_000);
        }
    }

    @Test
    void theEndpointRefusesAConnectionWithoutAValidTicket() throws Exception {
        try (var handler = handlerBundle(DirectStreamConfig.on(0, "127.0.0.1"))) {
            handler.registerRequestHandler(EXPORT, new Exporter(1));
            handler.start().get(5, TimeUnit.SECONDS);

            var intruder = new NettyClientTransport("intruder", "127.0.0.1", handler.directStreamPort(),
                    NettyTransportConfig.defaults());
            var answer = new CompletableFuture<Message>();
            intruder.onMessage(answer::complete);
            try {
                intruder.connect().get(5, TimeUnit.SECONDS);
                intruder.send(new Hello(UUID.randomUUID(), HandshakeProtocol.PROTOCOL_VERSION, "intruder", "i-1", "1",
                        Set.of(HandshakeProtocol.CAPABILITY_STREAM), "guessed-ticket", System.currentTimeMillis()));
                var reply = answer.get(5, TimeUnit.SECONDS);
                assertThat(reply).isInstanceOf(Reject.class);
                assertThat(((Reject) reply).code()).isEqualTo(Reject.CODE_AUTH_FAILED);
            } finally {
                intruder.close();
            }
        }
    }
}
