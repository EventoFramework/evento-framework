package com.evento.server.bus;

import com.evento.application.client.BundleClient;
import com.evento.application.client.EventoServerAdapter;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.performance.PerformanceInvocationsMessage;
import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.event.BusEvent;
import com.evento.server.bus.event.BusEventBus;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.registry.ClusterRegistry;
import com.evento.server.bus.registry.ConnectionRegistry;
import com.evento.server.bus.router.ForwardingTable;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Exercises the bundle → server admin-notification path landed in PR3.2b.
 *
 * <p>A real {@link BundleClient} wrapped in {@link EventoServerAdapter}
 * fires an {@link PerformanceInvocationsMessage} via
 * {@link EventoServerAdapter#send(java.io.Serializable)}. The server side
 * surfaces it as a {@link BusEvent.AdminNotification} that subscribers can
 * pattern-match — proving the wire convention agreed by
 * {@code EventoServerAdapter}, {@code BusLifecycle.onNotification}, and the
 * production {@code BundleAdminNotificationListener}.
 *
 * <p>Doesn't boot Spring — wires the listener-equivalent assertion directly
 * via {@code BusEventBus.subscribe} so the test stays focused on the wire
 * round-trip rather than DI plumbing.
 */
class AdminNotificationFlowIT {

    private NettyTransportConfig transportConfig;
    private BusLifecycle lifecycle;
    private BusEventBus eventBus;
    private int port;

    @BeforeEach
    void setUp() {
        transportConfig = new NettyTransportConfig(
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(5),
                16 * 1024 * 1024, 64 * 1024, 32 * 1024,
                new ExponentialBackoffWithJitter(), new JacksonCborCodec(),
                Executors.newVirtualThreadPerTaskExecutor());

        eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofMillis(100));
        var forwarding = new ForwardingTable();
        var server = new NettyServerTransport(transportConfig);
        lifecycle = new BusLifecycle(server, connections, cluster, correlations, forwarding,
                eventBus, "notif-it-server",
                Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                new JacksonCborPayloadCodec());
        port = lifecycle.start(0);
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(500));
    }

    @Test
    void eventoServerV2AdapterSendRoutesAsAdminNotification() throws Exception {
        var seen = new ArrayList<BusEvent.AdminNotification>();
        eventBus.subscribe(event -> {
            if (event instanceof BusEvent.AdminNotification an) seen.add(an);
        });

        BundleClient client = BundleClient.builder("bundle-A", "inst-A1")
                .host("127.0.0.1").port(port).bundleVersion("100")
                .transportConfig(transportConfig)
                .handlerPayloadTypes(List.of())
                .build();
        try (client) {
            client.start().get(3, TimeUnit.SECONDS);
            await().atMost(3, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 1);

            var adapter = new EventoServerAdapter(client, "bundle-A", "inst-A1", 100L);
            var metric = new PerformanceInvocationsMessage();
            metric.setBundle("bundle-A");
            metric.setInstanceId("inst-A1");
            metric.setComponent("DemoComponent");
            metric.setAction("DemoAction");
            metric.setInvocations(new HashMap<>());

            adapter.send(metric);

            await().atMost(3, TimeUnit.SECONDS).until(() -> !seen.isEmpty());
            var an = seen.getFirst();
            assertThat(an.payloadType()).isEqualTo(ProtocolPayloadTypes.BUNDLE_ADMIN_NOTIFICATION);
            assertThat(an.source().bundleId()).isEqualTo("bundle-A");

            // The envelope decodes back to the EventoMessage carrying the metric.
            var codec = new AdminPayloadCodec();
            var envelope = codec.decodeMessage(an.payload());
            assertThat(envelope.getSourceBundleId()).isEqualTo("bundle-A");
            assertThat(envelope.getSourceInstanceId()).isEqualTo("inst-A1");
            assertThat(envelope.getBody()).isInstanceOf(PerformanceInvocationsMessage.class);
            assertThat(((PerformanceInvocationsMessage) envelope.getBody()).getComponent())
                    .isEqualTo("DemoComponent");
        }
    }
}
