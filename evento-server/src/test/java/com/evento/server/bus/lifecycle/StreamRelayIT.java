package com.evento.server.bus.lifecycle;

import com.evento.application.client.BundleClient;
import com.evento.application.client.handler.HandlerRegistry;
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
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Streamed replies through the broker, at the frame level: the handler never runs more
 * than the caller's window ahead, frames arrive in order, and every way a stream can end
 * (its end, a cancel, either peer leaving) releases both sides and the broker.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class StreamRelayIT {

    private static final String EXPORT = "ExportOrders";
    private static final int WINDOW = 8;

    private BusLifecycle lifecycle;
    private int port;

    @BeforeEach
    void setUp() {
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        lifecycle = new BusLifecycle(new NettyServerTransport(NettyTransportConfig.defaults()),
                connections, new ClusterRegistry(connections), new CorrelationStore(Duration.ofMillis(100)),
                new ForwardingTable(), eventBus, "broker-stream", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                new JacksonCborPayloadCodec(), TokenValidator.acceptAll(),
                new ForwardingDedupCache(1000, Duration.ofMinutes(1)));
        port = lifecycle.start(0);
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(500));
    }

    private BundleClient client(String id, List<String> handled) {
        return BundleClient.builder(id, id + "-1")
                .host("127.0.0.1").port(port)
                .handlerPayloadTypes(handled)
                .transportConfig(NettyTransportConfig.defaults())
                .build();
    }

    private static byte[] frame(int index, int size) {
        var bytes = new byte[size];
        ByteBuffer.wrap(bytes).putInt(index);
        return bytes;
    }

    /** A streaming handler that sends {@code frames} frames, recording what it managed to send. */
    private static final class Exporter implements HandlerRegistry.StreamingRequestHandler {
        final int frames;
        final int frameSize;
        final AtomicInteger sent = new AtomicInteger();
        final AtomicReference<Throwable> stoppedBy = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);

        Exporter(int frames, int frameSize) {
            this.frames = frames;
            this.frameSize = frameSize;
        }

        @Override
        public byte[] handle(byte[] payload, HandlerRegistry.RequestContext context) {
            return "whole".getBytes();
        }

        @Override
        public void handleStream(byte[] payload, HandlerRegistry.RequestContext context,
                                 HandlerRegistry.StreamSink sink) throws Exception {
            try {
                for (int i = 0; i < frames; i++) {
                    sink.send(frame(i, frameSize));
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
            if (item instanceof InboundStream.Item.Frame(byte[] payload)) {
                indices.add(ByteBuffer.wrap(payload).getInt());
            }
        }
    }

    @Test
    void theHandlerRunsAtMostAWindowAheadAndEveryFrameArrivesInOrder() throws Exception {
        var exporter = new Exporter(200, 64 * 1024);
        try (var handler = client("reports", List.of(EXPORT)); var caller = client("web", List.of())) {
            handler.registerRequestHandler(EXPORT, exporter);
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);
            assertThat(caller.streamsAccepted()).isTrue();

            try (var stream = caller.openStream(EXPORT, new byte[0], Duration.ofSeconds(10), WINDOW, false)) {
                // Nothing consumed yet: the handler sends its window and waits for credit.
                await().atMost(5, TimeUnit.SECONDS).until(() -> exporter.sent.get() == WINDOW);
                Thread.sleep(300);
                assertThat(exporter.sent.get()).isEqualTo(WINDOW);
                assertThat(lifecycle.activeStreams()).isEqualTo(1);

                var received = drain(stream);
                assertThat(received).hasSize(200);
                for (int i = 0; i < received.size(); i++) assertThat(received.get(i)).isEqualTo(i);
            }
            assertThat(exporter.done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(exporter.stoppedBy.get()).isNull();
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.activeStreams() == 0);
            assertThat(lifecycle.streamFramesRelayed()).isGreaterThan(200);
            assertThat(caller.inboundStreams()).isZero();
            assertThat(handler.outboundStreams()).isZero();
        }
    }

    @Test
    void closingTheStreamEarlyStopsTheHandler() throws Exception {
        var exporter = new Exporter(10_000, 1024);
        try (var handler = client("reports", List.of(EXPORT)); var caller = client("web", List.of())) {
            handler.registerRequestHandler(EXPORT, exporter);
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            var stream = caller.openStream(EXPORT, new byte[0], Duration.ofSeconds(10), WINDOW, false);
            for (int i = 0; i < 3; i++) assertThat(stream.next()).isInstanceOf(InboundStream.Item.Frame.class);
            stream.close();

            assertThat(exporter.done.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(exporter.stoppedBy.get()).isInstanceOf(HandlerRegistry.StreamCancelledException.class);
            assertThat(exporter.sent.get()).isLessThan(10_000);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.activeStreams() == 0);
            await().atMost(5, TimeUnit.SECONDS).until(() -> handler.outboundStreams() == 0);
        }
    }

    @Test
    void aCallerThatLeavesMidStreamStopsTheHandler() throws Exception {
        var exporter = new Exporter(10_000, 1024);
        try (var handler = client("reports", List.of(EXPORT)); var caller = client("web", List.of())) {
            handler.registerRequestHandler(EXPORT, exporter);
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            var stream = caller.openStream(EXPORT, new byte[0], Duration.ofSeconds(10), WINDOW, false);
            assertThat(stream.next()).isInstanceOf(InboundStream.Item.Frame.class);
            caller.currentTransportForTest().close();

            assertThat(exporter.done.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(exporter.stoppedBy.get()).isInstanceOf(HandlerRegistry.StreamCancelledException.class);
            // The caller's side fails too: the stream cannot follow it to a new session.
            assertThatThrownBy(() -> drain(stream)).isInstanceOf(InboundStream.StreamFailedException.class);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.activeStreams() == 0);
        }
    }

    @Test
    void aHandlerThatLeavesMidStreamFailsTheCaller() throws Exception {
        var exporter = new Exporter(10_000, 1024);
        try (var handler = client("reports", List.of(EXPORT)); var caller = client("web", List.of())) {
            handler.registerRequestHandler(EXPORT, exporter);
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            var stream = caller.openStream(EXPORT, new byte[0], Duration.ofSeconds(10), WINDOW, false);
            assertThat(stream.next()).isInstanceOf(InboundStream.Item.Frame.class);
            handler.currentTransportForTest().close();

            assertThatThrownBy(() -> drain(stream))
                    .isInstanceOf(InboundStream.StreamFailedException.class)
                    .hasMessageContaining("disconnected");
            assertThat(exporter.done.await(10, TimeUnit.SECONDS)).isTrue();
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.activeStreams() == 0);
        }
    }

    @Test
    void aHandlerThatCannotStreamAnswersWholeAndTheCallerTakesIt() throws Exception {
        try (var handler = client("legacy", List.of(EXPORT)); var caller = client("web", List.of())) {
            handler.registerRequestHandler(EXPORT, (payload, ctx) -> "all at once".getBytes());
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            try (var stream = caller.openStream(EXPORT, new byte[0], Duration.ofSeconds(10), WINDOW, false)) {
                var first = stream.next();
                assertThat(first).isInstanceOf(InboundStream.Item.Whole.class);
                assertThat(new String(((InboundStream.Item.Whole) first).payload())).isEqualTo("all at once");
                assertThat(stream.next()).isInstanceOf(InboundStream.Item.End.class);
            }
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.activeStreams() == 0);
        }
    }

    @Test
    void aCallerThatStopsConsumingTimesOutAndReleasesTheHandler() throws Exception {
        var exporter = new Exporter(10_000, 1024);
        try (var handler = client("reports", List.of(EXPORT)); var caller = client("web", List.of())) {
            handler.registerRequestHandler(EXPORT, exporter);
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(5, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            // Opened and never read: the handler fills the window, then gives the caller up
            // after the stream's timeout instead of holding its source forever.
            caller.openStream(EXPORT, new byte[0], Duration.ofSeconds(1), WINDOW, false);
            assertThat(exporter.done.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(exporter.sent.get()).isEqualTo(WINDOW);
            assertThat(exporter.stoppedBy.get()).isInstanceOf(HandlerRegistry.StreamCancelledException.class);
        }
    }
}
