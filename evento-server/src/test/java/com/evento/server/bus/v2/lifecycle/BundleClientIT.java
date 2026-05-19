package com.evento.server.bus.v2.lifecycle;

import com.evento.application.client.v2.BundleClient;
import com.evento.application.client.v2.BundleClientState;
import com.evento.server.bus.v2.correlation.CorrelationStore;
import com.evento.server.bus.v2.correlation.ForwardingDedupCache;
import com.evento.server.bus.v2.event.BusEventBus;
import com.evento.server.bus.v2.registry.ClusterRegistry;
import com.evento.server.bus.v2.registry.ConnectionRegistry;
import com.evento.server.bus.v2.router.ForwardingTable;
import com.evento.server.bus.v2.security.TokenValidator;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.SendFailedException;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.message.Hello;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import com.evento.transport.netty.NettyClientTransport;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.protocol.BundleRegistrationInfo;
import com.evento.transport.protocol.ProtocolNotifications;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end exercise of the production-shape Bundle ↔ Server v2 stack:
 *
 * <ul>
 *   <li>Real TCP transport (Netty) on both ends.</li>
 *   <li>Two {@link BundleClient}s — one registers a handler, the other calls it
 *       through the broker; the {@link BusLifecycle} forwards Request →
 *       handler bundle, and Response → caller.</li>
 *   <li>Exactly-once at the broker: identical correlation ids resolve to a
 *       single execution.</li>
 *   <li>Token-based auth at handshake; bad token → Reject.</li>
 *   <li>TLS-encrypted variant of the round-trip via a self-signed cert.</li>
 * </ul>
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class BundleClientIT {

    private static final String GOOD_TOKEN = "let-me-in";

    private BusLifecycle lifecycle;
    private int port;
    private NettyTransportConfig serverConfig;
    private NettyTransportConfig clientConfig;

    @BeforeEach
    void setUp() {
        serverConfig = NettyTransportConfig.defaults();
        clientConfig = NettyTransportConfig.defaults();
        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofMillis(100));
        var forwarding = new ForwardingTable();
        var dedup = new ForwardingDedupCache(1000, Duration.ofMinutes(1));
        var server = new NettyServerTransport(serverConfig);
        lifecycle = new BusLifecycle(server, connections, cluster, correlations, forwarding, eventBus,
                "broker-it", Set.of(HandshakeProtocol.CAPABILITY_PING_PONG),
                new JacksonCborPayloadCodec(),
                TokenValidator.sharedSecret(GOOD_TOKEN),
                dedup);
        port = lifecycle.start(0);
    }

    @AfterEach
    void tearDown() {
        lifecycle.stop(Duration.ofMillis(500));
    }

    private BundleClient buildClient(String bundleId, String instanceId,
                                      List<String> handlerTypes, String token) {
        return BundleClient.builder(bundleId, instanceId)
                .host("127.0.0.1").port(port)
                .bundleVersion("100")
                .authToken(token)
                .handlerPayloadTypes(handlerTypes)
                .transportConfig(clientConfig)
                .defaultRequestTimeout(Duration.ofSeconds(5))
                .build();
    }

    // ----------------------------------------------------------------------
    // Happy path: caller bundle → broker → handler bundle → broker → caller
    // ----------------------------------------------------------------------

    @Test
    void requestRoutedToHandlerBundleAndResponseFlowsBack() throws Exception {
        try (var handler = buildClient("handler-bundle", "handler-1",
                List.of("com.evento.test.Cmd"), GOOD_TOKEN);
             var caller = buildClient("caller-bundle", "caller-1",
                     List.of(), GOOD_TOKEN)) {

            var handlerInvocations = new AtomicInteger();
            handler.registerRequestHandler("com.evento.test.Cmd", (payload, ctx) -> {
                handlerInvocations.incrementAndGet();
                String text = new String(payload);
                return ("echo:" + text).getBytes();
            });

            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);

            await().atMost(3, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            var response = caller.request("com.evento.test.Cmd", "ping".getBytes())
                    .get(5, TimeUnit.SECONDS);
            assertThat(response.isError()).isFalse();
            assertThat(new String(response.payload())).isEqualTo("echo:ping");
            assertThat(handlerInvocations.get()).isEqualTo(1);
        }
    }

    // ----------------------------------------------------------------------
    // Handler throws → caller receives Response.failure with the exception class
    // ----------------------------------------------------------------------

    @Test
    void handlerExceptionPropagatesAsResponseError() throws Exception {
        try (var handler = buildClient("h", "h1", List.of("com.X"), GOOD_TOKEN);
             var caller = buildClient("c", "c1", List.of(), GOOD_TOKEN)) {

            handler.registerRequestHandler("com.X", (p, ctx) -> {
                throw new IllegalArgumentException("nope");
            });
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(3, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            var response = caller.request("com.X", new byte[0]).get(5, TimeUnit.SECONDS);
            assertThat(response.isError()).isTrue();
            assertThat(response.error().exceptionClassName()).isEqualTo(IllegalArgumentException.class.getName());
            assertThat(response.error().message()).isEqualTo("nope");
        }
    }

    // ----------------------------------------------------------------------
    // Bad token → Reject(AUTH_FAILED) → supervisor never reaches READY
    // ----------------------------------------------------------------------

    @Test
    void wrongTokenIsRejectedAtHandshake() {
        var bundle = buildClient("rogue", "r1", List.of(), "wrong-token");
        assertThatThrownBy(() -> bundle.start().get(3, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("AUTH_FAILED");
        assertThat(bundle.state()).isNotEqualTo(BundleClientState.READY);
        bundle.close();
    }

    @Test
    void missingTokenWhenServerRequiresOneIsRejected() {
        var bundle = buildClient("rogue", "r1", List.of(), null);
        assertThatThrownBy(() -> bundle.start().get(3, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("AUTH_FAILED");
        bundle.close();
    }

    // ----------------------------------------------------------------------
    // Bundle-side dedup: a duplicate inbound Request is served from cache
    // without invoking the handler twice.
    // ----------------------------------------------------------------------

    @Test
    void duplicateInboundRequestReplaysCachedResponseWithoutReinvokingHandler() throws Exception {
        try (var handler = buildClient("h", "h1", List.of("com.dup"), GOOD_TOKEN)) {
            var invocations = new AtomicInteger();
            handler.registerRequestHandler("com.dup", (payload, ctx) -> {
                invocations.incrementAndGet();
                return ("processed:" + new String(payload)).getBytes();
            });
            handler.start().get(5, TimeUnit.SECONDS);
            await().atMost(3, TimeUnit.SECONDS).until(() -> lifecycle.isBundleAvailable("h"));

            // Bypass BundleClient to control the correlationId — send the same Request twice.
            var rawCallerConfig = NettyTransportConfig.defaults();
            var raw = new NettyClientTransport("dup-tester", "127.0.0.1", port, rawCallerConfig);
            try {
                var responses = new java.util.concurrent.ConcurrentLinkedQueue<Response>();
                raw.onMessage(msg -> { if (msg instanceof Response r) responses.add(r); });
                raw.connect().get(2, TimeUnit.SECONDS);

                var helloCorr = UUID.randomUUID();
                var helloAck = new CompletableFuture<Void>();
                raw.onMessage(msg -> {
                    if (msg instanceof com.evento.transport.message.Welcome w
                            && w.correlationId().equals(helloCorr)) helloAck.complete(null);
                    else if (msg instanceof Response r) responses.add(r);
                });
                raw.send(new Hello(helloCorr, HandshakeProtocol.PROTOCOL_VERSION,
                        "dup-tester", "dup-tester-i", "100", Set.of(),
                        GOOD_TOKEN, System.currentTimeMillis()));
                helloAck.get(2, TimeUnit.SECONDS);

                var codec = new JacksonCborPayloadCodec();
                raw.send(new Notification(UUID.randomUUID(),
                        BundleRegistrationInfo.PAYLOAD_TYPE,
                        codec.encode(new BundleRegistrationInfo(100L, List.of(), Map.of())),
                        System.currentTimeMillis())).get();
                raw.send(new Notification(UUID.randomUUID(),
                        ProtocolNotifications.ENABLE, new byte[0],
                        System.currentTimeMillis())).get();
                await().atMost(2, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

                var sharedCorr = UUID.randomUUID();
                var request = new Request(sharedCorr, "dup-tester", "dup-tester-i", "100",
                        "com.dup", "hello".getBytes(), 5000L, System.currentTimeMillis());

                raw.send(request);
                // Wait for the first response then issue the duplicate.
                await().atMost(2, TimeUnit.SECONDS).until(() -> !responses.isEmpty());
                raw.send(request);
                // Both should now resolve — second one is a replay.
                await().atMost(2, TimeUnit.SECONDS).until(() -> responses.size() == 2);

                assertThat(invocations.get())
                        .as("handler should run exactly once across the duplicate")
                        .isEqualTo(1);
                for (var resp : responses) {
                    assertThat(resp.correlationId()).isEqualTo(sharedCorr);
                    assertThat(new String(resp.payload())).isEqualTo("processed:hello");
                }
            } finally {
                raw.close();
            }
        }
    }

    // ----------------------------------------------------------------------
    // Reconnect: closing the transport from underneath the supervisor causes
    // it to come back up and re-register handlers, transparently.
    // ----------------------------------------------------------------------

    @Test
    void supervisorReconnectsAndRegistersHandlersAfterTransportDrop() throws Exception {
        try (var handler = buildClient("h", "h-rc", List.of("com.rc"), GOOD_TOKEN);
             var caller = buildClient("c", "c-rc", List.of(), GOOD_TOKEN)) {

            handler.registerRequestHandler("com.rc", (p, ctx) -> "ok".getBytes());

            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(3, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            // Yank the handler's transport from under it; the supervisor must reconnect.
            handler.disable();  // graceful drain first
            await().atMost(2, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 1);
            handler.currentTransportForTest().close();

            await().atMost(5, TimeUnit.SECONDS).until(() ->
                    handler.state() == BundleClientState.READY
                            && lifecycle.isBundleAvailable("h"));

            var response = caller.request("com.rc", new byte[0]).get(5, TimeUnit.SECONDS);
            assertThat(response.isError()).isFalse();
            assertThat(new String(response.payload())).isEqualTo("ok");
        }
    }

    // ----------------------------------------------------------------------
    // TLS: the same round-trip runs over an encrypted socket. Uses a self-
    // signed cert so the test is hermetic.
    // ----------------------------------------------------------------------

    @Test
    void encryptedRoundTripWithSelfSignedTls() throws Exception {
        SelfSignedCertificate ssc;
        try {
            ssc = new SelfSignedCertificate();
        } catch (Throwable t) {
            // Netty 4.1's SelfSignedCertificate falls back through sun.security helpers
            // that have been progressively restricted in newer JDKs. Skip the test on
            // platforms where no provider can mint a cert — the TLS pipeline wiring is
            // exercised in production via real certs anyway.
            Assumptions.assumeTrue(false,
                    "no provider can mint a self-signed certificate on this JDK: " + t);
            return;
        }

        // Tear down the unencrypted server set up by @BeforeEach and replace it with a TLS one.
        lifecycle.stop(Duration.ofMillis(200));
        var serverSsl = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .clientAuth(ClientAuth.NONE)
                .build();
        var clientSsl = SslContextBuilder.forClient()
                .trustManager(ssc.certificate())
                .build();

        var tlsServerConfig = NettyTransportConfig.defaults().withSslContext(serverSsl);
        var tlsClientConfig = NettyTransportConfig.defaults().withSslContext(clientSsl);

        var eventBus = new BusEventBus();
        var connections = new ConnectionRegistry(eventBus);
        var cluster = new ClusterRegistry(connections);
        var correlations = new CorrelationStore(Duration.ofMillis(100));
        var forwarding = new ForwardingTable();
        var dedup = new ForwardingDedupCache(1000, Duration.ofMinutes(1));
        var server = new NettyServerTransport(tlsServerConfig);
        lifecycle = new BusLifecycle(server, connections, cluster, correlations, forwarding, eventBus,
                "broker-tls", Set.of(),
                new JacksonCborPayloadCodec(),
                TokenValidator.sharedSecret(GOOD_TOKEN),
                dedup);
        port = lifecycle.start(0);

        try (var handler = BundleClient.builder("h", "h1")
                .host("127.0.0.1").port(port).bundleVersion("100")
                .authToken(GOOD_TOKEN)
                .handlerPayloadTypes(List.of("com.tls"))
                .transportConfig(tlsClientConfig).build();
             var caller = BundleClient.builder("c", "c1")
                     .host("127.0.0.1").port(port).bundleVersion("100")
                     .authToken(GOOD_TOKEN)
                     .transportConfig(tlsClientConfig).build()) {

            handler.registerRequestHandler("com.tls",
                    (p, ctx) -> ("tls-ok:" + new String(p)).getBytes());
            handler.start().get(5, TimeUnit.SECONDS);
            caller.start().get(5, TimeUnit.SECONDS);
            await().atMost(3, TimeUnit.SECONDS).until(() -> lifecycle.availableView().size() == 2);

            var response = caller.request("com.tls", "encrypted".getBytes()).get(5, TimeUnit.SECONDS);
            assertThat(response.isError()).isFalse();
            assertThat(new String(response.payload())).isEqualTo("tls-ok:encrypted");
        }
    }

    // ----------------------------------------------------------------------
    // Request sent before the supervisor has finished its handshake fails fast
    // (no infinite wait).
    // ----------------------------------------------------------------------

    @Test
    void requestBeforeStartFailsFast() {
        try (var bundle = buildClient("idle", "idle1", List.of(), GOOD_TOKEN)) {
            var future = bundle.request("com.X", new byte[0]);
            // The request future surfaces a Response.failure(SendFailedException) immediately.
            var response = future.join();
            assertThat(response.isError()).isTrue();
            assertThat(response.error().exceptionClassName()).contains("SendFailedException");
        }
    }
}
