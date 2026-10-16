package com.evento.lab.streaming;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.client.EventoServerAdapter;
import com.evento.application.client.stream.DirectStreamConfig;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.common.messaging.gateway.QueryOptions;
import com.evento.common.messaging.gateway.QueryStream;
import com.evento.common.modeling.messaging.query.PageRequest;
import com.evento.common.modeling.messaging.query.Streamed;
import com.evento.lab.support.CommandAwareEmbeddedBroker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * {@code QueryGateway.stream} end to end, with real bundles: any query handler can be
 * streamed (the old answer types included), a {@code Streamed} answer is produced only as
 * fast as it is consumed, failures surface as the exception the query threw, the bundle's
 * own queries stream in process with the same tracing, and a direct stream bypasses the
 * broker.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class QueryStreamingIT {

    private CommandAwareEmbeddedBroker broker;
    private CommandAwareEmbeddedBroker.TestGatewayClient caller;
    private EventoBundle bundle;
    private CountingTracingAgent tracing;

    @BeforeEach
    void setUp() throws Exception {
        StreamingStore.reset();
        broker = new CommandAwareEmbeddedBroker();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (caller != null) caller.close();
        if (bundle != null) {
            bundle.getEngineSupervisor().stop(Duration.ofSeconds(10));
            ((EventoServerAdapter) bundle.getEventoServer()).close();
        }
        if (broker != null) broker.close();
    }

    private void startBundle(DirectStreamConfig direct) throws Exception {
        tracing = new CountingTracingAgent("export-bundle");
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(ExportProjection.class.getPackage())
                .setBundleId("export-bundle")
                .setTracingAgent(tracing)
                .setDirectStreamConfig(direct)
                // Small frames, so even modest results take many frames.
                .setStreamFrameBytes(8 * 1024)
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();
        await().atMost(20, TimeUnit.SECONDS).until(() -> broker.lifecycle().isBundleAvailable("export-bundle"));
        caller = broker.newGatewayClient();
    }

    private static List<Integer> indices(QueryStream<ExportView> views) {
        try (views) {
            return views.stream().map(ExportView::getIndex).toList();
        }
    }

    @Test
    void anOldMultipleAnswerStreamsThroughTheBrokerInOrder() throws Exception {
        startBundle(null);
        long opened = broker.lifecycle().streamsOpened();

        var received = indices(caller.queryGateway().stream(new ExportAllQuery(20_000)));

        assertThat(received).hasSize(20_000);
        for (int i = 0; i < received.size(); i++) assertThat(received.get(i)).isEqualTo(i);
        assertThat(broker.lifecycle().streamsOpened()).isEqualTo(opened + 1);
        assertThat(broker.lifecycle().streamFramesRelayed()).isGreaterThan(10);
        assertThat(tracing.trackedQueries.get()).isEqualTo(1);
        await().atMost(5, TimeUnit.SECONDS).until(() -> broker.lifecycle().activeStreams() == 0);
    }

    @Test
    void aStreamedAnswerIsProducedAsConsumedAndClosedWhenTheCallerStops() throws Exception {
        startBundle(null);

        try (var views = caller.queryGateway().stream(new ExportLazyQuery(1_000_000),
                QueryOptions.defaults().window(4))) {
            var it = views.iterator();
            for (int i = 0; i < 100; i++) assertThat(it.next().getIndex()).isEqualTo(i);
        }

        // A million views were never produced: the source ran a window ahead, then stopped
        // and was closed when the caller closed the stream.
        await().atMost(10, TimeUnit.SECONDS).until(() -> StreamingStore.closed.get() == 1);
        assertThat(StreamingStore.opened.get()).isEqualTo(1);
        assertThat(StreamingStore.produced.get()).isLessThan(100_000);
    }

    @Test
    void aStreamedAnswerReadToTheEndClosesItsSource() throws Exception {
        startBundle(null);

        assertThat(indices(caller.queryGateway().stream(new ExportLazyQuery(5_000)))).hasSize(5_000);
        await().atMost(5, TimeUnit.SECONDS).until(() -> StreamingStore.closed.get() == 1);
    }

    @Test
    void aPlainQueryOfAStreamedHandlerGetsEveryView() throws Exception {
        startBundle(null);

        Streamed<ExportView> whole = caller.queryGateway()
                .query(new ExportLazyQuery(3_000)).get(30, TimeUnit.SECONDS);

        assertThat(whole.getData()).hasSize(3_000);
        assertThat(StreamingStore.closed.get()).isEqualTo(1);
    }

    @Test
    void aSourceThatBreaksMidWaySurfacesAfterTheViewsBeforeIt() throws Exception {
        startBundle(null);
        var received = new ArrayList<Integer>();

        assertThatThrownBy(() -> {
            try (var views = caller.queryGateway().stream(new ExportLazyQuery(10_000).failingAt(4_000))) {
                for (var view : views) received.add(view.getIndex());
            }
        }).isInstanceOf(QueryStream.QueryStreamException.class)
                .cause().isInstanceOf(IllegalStateException.class).hasMessageContaining("source broke at 4000");
        assertThat(received).isNotEmpty().hasSizeLessThanOrEqualTo(4_000);
        assertThat(received.getLast()).isEqualTo(received.size() - 1);
    }

    @Test
    void aRefusedQueryThrowsWhatItsHandlerThrew() throws Exception {
        startBundle(null);

        assertThatThrownBy(() -> indices(caller.queryGateway().stream(new RejectedQuery())))
                .isInstanceOf(QueryStream.QueryStreamException.class)
                .cause().isInstanceOf(IllegalArgumentException.class).hasMessage("this query is refused");
    }

    @Test
    void aPageAnswerStreamsItsViews() throws Exception {
        startBundle(null);

        var received = indices(caller.queryGateway().stream(new ExportPageQuery(1_000, PageRequest.of(2, 50))));

        assertThat(received).hasSize(50).first().isEqualTo(100);
    }

    @Test
    void theBundlesOwnQueryStreamsInProcessWithTheSameTracing() throws Exception {
        startBundle(null);
        var client = ((EventoServerAdapter) bundle.getEventoServer()).client();
        long local = client.localQueries();
        long relayed = broker.lifecycle().streamsOpened();

        var received = indices(bundle.getQueryGateway().stream(new ExportAllQuery(2_000)));

        assertThat(received).hasSize(2_000);
        assertThat(client.localQueries()).isEqualTo(local + 1);
        assertThat(broker.lifecycle().streamsOpened()).isEqualTo(relayed);
        // Tracked exactly as a query arriving through the broker.
        assertThat(tracing.trackedQueries.get()).isEqualTo(1);
    }

    @Test
    void theBundlesOwnPlainQueryIsAnsweredInProcessWithTheSameTracing() throws Exception {
        startBundle(null);
        var client = ((EventoServerAdapter) bundle.getEventoServer()).client();
        long forwarded = broker.lifecycle().forwardedRawCount();

        var whole = bundle.getQueryGateway().query(new ExportAllQuery(10)).get(10, TimeUnit.SECONDS);

        assertThat(whole.getData()).hasSize(10);
        assertThat(client.localQueries()).isEqualTo(1);
        assertThat(broker.lifecycle().forwardedRawCount()).isEqualTo(forwarded);
        assertThat(tracing.trackedQueries.get()).isEqualTo(1);
        // The handler-side latency is recorded as for a remote query.
        assertThat(client.latencies().handlers().snapshot()).containsKey("query");
    }

    @Test
    void aDirectStreamBypassesTheBroker() throws Exception {
        startBundle(DirectStreamConfig.on(0, "127.0.0.1"));
        var handler = ((EventoServerAdapter) bundle.getEventoServer()).client();
        long bytesBefore = broker.lifecycle().streamBytesRelayed();

        var received = indices(caller.queryGateway().stream(new ExportAllQuery(20_000),
                QueryOptions.defaults().direct()));

        assertThat(received).hasSize(20_000);
        assertThat(handler.directStreamsServed()).isEqualTo(1);
        assertThat(caller.client().directStreamsFollowed()).isEqualTo(1);
        assertThat(broker.lifecycle().streamBytesRelayed() - bytesBefore).isLessThan(4_096);
    }
}
